package com.tedredington.jazzclub.lastfm;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LastFmClient} over HTTPS: signed form posts to the 2.0 API, JSON answers read as a tree so the
 * native image needs no reflection metadata. See https://www.last.fm/api.
 */
final class HttpLastFmClient implements LastFmClient {

    private static final Logger log = LoggerFactory.getLogger(HttpLastFmClient.class);

    private final RestClient restClient;
    private final URI endpoint;
    private final String apiKey;
    private final String apiSecret;
    private final JsonMapper json = JsonMapper.shared();

    HttpLastFmClient(RestClient restClient, URI endpoint, String apiKey, String apiSecret) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public Session signIn(String user, String password) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("username", user);
        parameters.put("password", password);
        JsonNode session = call("auth.getMobileSession", parameters).path("session");
        String name = session.path("name").asString("");
        String key = session.path("key").asString("");
        if (key.isEmpty()) {
            throw new LastFmException.TemporarilyUnavailable(LastFmException.NO_CODE,
                    "Last.fm signed you in but sent no session key", null);
        }
        return new Session(name.isEmpty() ? user : name, key);
    }

    @Override
    public void nowPlaying(String sessionKey, Track track) {
        Map<String, String> parameters = trackParameters(track, "");
        parameters.put("sk", sessionKey);
        call("track.updateNowPlaying", parameters);
    }

    @Override
    public void scrobble(String sessionKey, Scrobble scrobble) {
        // the indexed form takes up to 50 scrobbles per request; one for now
        Map<String, String> parameters = trackParameters(scrobble.track(), "[0]");
        parameters.put("timestamp[0]", Long.toString(scrobble.startedAt().getEpochSecond()));
        // Pandora picks the songs, not the listener; Last.fm treats radio plays this way
        parameters.put("chosenByUser[0]", "0");
        parameters.put("sk", sessionKey);
        JsonNode attributes = call("track.scrobble", parameters).path("scrobbles").path("@attr");
        if (attributes.path("ignored").asInt(0) > 0) {
            // e.g. a timestamp too far in the past; retrying will not change Last.fm's mind
            log.info("Last.fm ignored the scrobble of '{}' by '{}'", scrobble.track().title(),
                    scrobble.track().artist());
        }
    }

    @Override
    public void love(String sessionKey, Track track) {
        call("track.love", identity(sessionKey, track));
    }

    @Override
    public void unlove(String sessionKey, Track track) {
        call("track.unlove", identity(sessionKey, track));
    }

    /** {@code track.love} and {@code track.unlove} take only artist and title, nothing else. */
    private static Map<String, String> identity(String sessionKey, Track track) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("artist", track.artist());
        parameters.put("track", track.title());
        parameters.put("sk", sessionKey);
        return parameters;
    }

    private static Map<String, String> trackParameters(Track track, String index) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("artist" + index, track.artist());
        parameters.put("track" + index, track.title());
        if (track.album() != null) {
            parameters.put("album" + index, track.album());
        }
        if (track.length().isPositive()) {
            parameters.put("duration" + index, Long.toString(track.length().toSeconds()));
        }
        return parameters;
    }

    private JsonNode call(String method, Map<String, String> arguments) {
        Map<String, String> parameters = new LinkedHashMap<>(arguments);
        parameters.put("method", method);
        parameters.put("api_key", apiKey);
        parameters.put("api_sig", ApiSignature.sign(parameters, apiSecret));
        parameters.put("format", "json");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parameters.forEach(form::add);

        Response response;
        try {
            response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, reply) -> new Response(reply.getStatusCode().value(),
                            StreamUtils.copyToString(reply.getBody(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            throw new LastFmException.TemporarilyUnavailable(LastFmException.NO_CODE,
                    "could not reach " + endpoint.getHost() + " (" + root.getClass().getSimpleName() + ")", root);
        }
        if (response == null) {
            throw new LastFmException.TemporarilyUnavailable(LastFmException.NO_CODE, "no answer", null);
        }
        log.info("Last.fm {}: HTTP {}", method, response.status());
        if (!method.startsWith("auth.")) { // those answers carry the session key
            log.debug("Last.fm {} answered: {}", method, response.body());
        }
        return parse(response);
    }

    private JsonNode parse(Response response) {
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (JacksonException e) {
            root = null;
        }
        if (root != null && root.has("error")) {
            throw LastFmException.of(root.path("error").asInt(LastFmException.NO_CODE),
                    root.path("message").asString(""));
        }
        if (response.status() >= 500 || root == null || !root.isObject()) {
            throw new LastFmException.TemporarilyUnavailable(LastFmException.NO_CODE,
                    "unexpected answer, HTTP " + response.status(), null);
        }
        if (response.status() >= 400) {
            throw new LastFmException.Rejected(LastFmException.NO_CODE, "HTTP " + response.status());
        }
        return root;
    }

    private record Response(int status, String body) {
    }
}
