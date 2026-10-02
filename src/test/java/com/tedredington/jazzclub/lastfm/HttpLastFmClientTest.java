package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpLastFmClientTest {

    private static final URI ENDPOINT = URI.create("https://ws.example/2.0/");
    private static final Track NARDIS =
            new Track("Bill Evans", "Nardis", "Explorations", Duration.ofSeconds(351));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpLastFmClient client = new HttpLastFmClient(builder.build(), ENDPOINT, "KEY", "SECRET");

    /** What a request must carry besides its own parameters, signature included. */
    private static Map<String, String> signed(Map<String, String> parameters) {
        Map<String, String> all = new HashMap<>(parameters);
        all.put("api_key", "KEY");
        all.put("api_sig", ApiSignature.sign(all, "SECRET"));
        all.put("format", "json");
        return all;
    }

    @Test
    void signingInTradesThePasswordForASessionKey() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(signed(Map.of(
                        "method", "auth.getMobileSession", "username", "ted", "password", "pässword"))))
                .andRespond(withSuccess("{\"session\":{\"name\":\"Ted\",\"key\":\"sk1\",\"subscriber\":0}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.signIn("ted", "pässword")).isEqualTo(new Session("Ted", "sk1"));
        server.verify();
    }

    @Test
    void aSessionWithoutAKeyIsNotASession() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{\"session\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.signIn("ted", "pw"))
                .isInstanceOf(LastFmException.TemporarilyUnavailable.class);
    }

    @Test
    void nowPlayingSendsTheTrackAndTheSession() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(signed(Map.of(
                        "method", "track.updateNowPlaying", "sk", "sk1", "artist", "Bill Evans",
                        "track", "Nardis", "album", "Explorations", "duration", "351"))))
                .andRespond(withSuccess("{\"nowplaying\":{}}", MediaType.APPLICATION_JSON));

        client.nowPlaying("sk1", NARDIS);
        server.verify();
    }

    @Test
    void aScrobbleIsFiledUnderItsStartTimeAndMarkedAsNotChosenByTheListener() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(signed(Map.of(
                        "method", "track.scrobble", "sk", "sk1", "artist[0]", "Bill Evans", "track[0]", "Nardis",
                        "album[0]", "Explorations", "duration[0]", "351", "timestamp[0]", "1790000000",
                        "chosenByUser[0]", "0"))))
                .andRespond(withSuccess("{\"scrobbles\":{\"@attr\":{\"accepted\":1,\"ignored\":0}}}",
                        MediaType.APPLICATION_JSON));

        client.scrobble("sk1", new Scrobble(NARDIS, Instant.ofEpochSecond(1_790_000_000L)));
        server.verify();
    }

    @Test
    void lovingAndUnlovingSendOnlyArtistAndTitle() {
        for (String method : new String[] {"track.love", "track.unlove"}) {
            server.expect(requestTo(ENDPOINT))
                    .andExpect(content().formDataContains(signed(Map.of(
                            "method", method, "sk", "sk1", "artist", "Bill Evans", "track", "Nardis"))))
                    .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                            .doesNotContain("album", "duration"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }

        client.love("sk1", NARDIS);
        client.unlove("sk1", NARDIS);
        server.verify();
    }

    @Test
    void anIgnoredScrobbleIsNotAnError() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"scrobbles\":{\"@attr\":{\"accepted\":0,\"ignored\":1}}}",
                        MediaType.APPLICATION_JSON));

        client.scrobble("sk1", new Scrobble(NARDIS, Instant.EPOCH));
        server.verify();
    }

    @Test
    void unknownAlbumAndLengthAreLeftOut() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                        .contains("artist=").doesNotContain("album", "duration"))
                .andRespond(withSuccess("{\"nowplaying\":{}}", MediaType.APPLICATION_JSON));

        client.nowPlaying("sk1", new Track("Bill Evans", "Nardis", null, Duration.ZERO));
        server.verify();
    }

    @Test
    void lastFmErrorsBecomeTypedExceptionsWhateverTheHttpStatus() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":4,\"message\":\"Authentication Failed - You do not have permissions\"}"));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                "{\"error\":9,\"message\":\"Invalid session key - Please re-authenticate\"}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.signIn("ted", "wrong"))
                .isInstanceOf(LastFmException.AuthenticationFailed.class)
                .hasMessageContaining("Authentication Failed");
        assertThatThrownBy(() -> client.nowPlaying("old", NARDIS))
                .isInstanceOf(LastFmException.AuthenticationFailed.class);
    }

    @Test
    void serverTroubleWithoutALastFmErrorIsTemporary() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>oops</html>"));

        assertThatThrownBy(() -> client.nowPlaying("sk1", NARDIS))
                .isInstanceOf(LastFmException.TemporarilyUnavailable.class)
                .hasMessageContaining("502");
    }

    @Test
    void aClientErrorWithoutALastFmErrorIsRejected() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.nowPlaying("sk1", NARDIS))
                .isInstanceOf(LastFmException.Rejected.class)
                .hasMessageContaining("404");
    }

    @Test
    void networkFailuresAreTemporaryAndNameOnlyTheHost() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new SocketTimeoutException("timed out")));

        assertThatThrownBy(() -> client.scrobble("sk1", new Scrobble(NARDIS, Instant.EPOCH)))
                .isInstanceOf(LastFmException.TemporarilyUnavailable.class)
                .hasMessageContaining("ws.example")
                .hasMessageContaining("SocketTimeoutException");
    }
}
