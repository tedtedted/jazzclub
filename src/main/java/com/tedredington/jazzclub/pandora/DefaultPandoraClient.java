package com.tedredington.jazzclub.pandora;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import com.tedredington.jazzclub.pandora.error.InvalidLoginException;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraErrorCode;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Speaks Pandora's JSON API v5 the way pianobar does, with one deliberate difference: every call
 * uses HTTPS, where pianobar still sends some over plain HTTP.
 *
 * <p>JSON is handled through Jackson's tree model rather than data binding. That keeps parsing as
 * lenient as pianobar's, and needs no reflection, hence no GraalVM hints.
 */
public final class DefaultPandoraClient implements PandoraClient {

    private static final String RPC_PATH = "/services/json/";
    private static final String API_VERSION = "5";

    private final PandoraTransport transport;
    private final PartnerCredentials partner;
    private final PandoraCipher cipher;
    private final URI baseUri;
    private final InstantSource clock;
    private final JsonMapper json = JsonMapper.shared();
    private final PandoraResponseParser parser = new PandoraResponseParser(json);

    private UserCredentials credentials;
    private Session session;

    /** @param baseUri scheme, host and port of the API, e.g. {@code https://tuner.pandora.com:443} */
    public DefaultPandoraClient(PandoraTransport transport, PartnerCredentials partner, URI baseUri,
                                InstantSource clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.partner = Objects.requireNonNull(partner, "partner");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cipher = PandoraCipher.forPartner(partner);
    }

    @Override
    public synchronized void login(UserCredentials credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        authenticate();
    }

    @Override
    public synchronized List<Station> stations() {
        ObjectNode body = json.createObjectNode().put("returnAllStations", true);
        return parser.stations(call("user.getStationList", body));
    }

    @Override
    public synchronized List<Song> playlist(Station station, AudioQuality quality) {
        ObjectNode body = json.createObjectNode()
                .put("stationToken", station.token())
                .put("includeTrackLength", true);
        return parser.playlist(call("station.getPlaylist", body), quality);
    }

    /** An authenticated call. An expired token triggers one transparent re-login, like pianobar. */
    private JsonNode call(String method, ObjectNode body) {
        if (session == null) {
            throw new IllegalStateException("login() must succeed before calling " + method);
        }
        try {
            return callOnce(method, body);
        } catch (PandoraApiException e) {
            if (e.errorCode() != PandoraErrorCode.INVALID_AUTH_TOKEN) {
                throw e;
            }
            authenticate();
            return callOnce(method, body);
        }
    }

    private JsonNode callOnce(String method, ObjectNode body) {
        ObjectNode authenticated = body.deepCopy()
                .put("userAuthToken", session.userAuthToken())
                .put("syncTime", syncTime(session.timeOffsetSeconds()));
        String query = "method=" + method
                + "&auth_token=" + urlEncode(session.userAuthToken())
                + "&partner_id=" + session.partnerId()
                + "&user_id=" + urlEncode(session.listenerId());
        return parser.result(transport.post(uri(query), cipher.encrypt(json.writeValueAsString(authenticated))));
    }

    private void authenticate() {
        session = null;

        ObjectNode partnerLogin = json.createObjectNode()
                .put("username", partner.user())
                .put("password", partner.password())
                .put("deviceModel", partner.deviceModel())
                .put("version", API_VERSION)
                .put("includeUrls", true);
        // the only request that is sent unencrypted
        JsonNode partnerResult = parser.result(
                transport.post(uri("method=auth.partnerLogin"), json.writeValueAsString(partnerLogin)));

        long timeOffset = clock.instant().getEpochSecond()
                - cipher.decryptSyncTime(parser.requiredText(partnerResult, "syncTime"));
        String partnerAuthToken = parser.requiredText(partnerResult, "partnerAuthToken");
        String partnerId = parser.requiredText(partnerResult, "partnerId");

        ObjectNode userLogin = json.createObjectNode()
                .put("loginType", "user")
                .put("username", credentials.username())
                .put("password", credentials.password())
                .put("partnerAuthToken", partnerAuthToken)
                .put("syncTime", syncTime(timeOffset));
        String query = "method=auth.userLogin&auth_token=" + urlEncode(partnerAuthToken) + "&partner_id=" + partnerId;
        JsonNode userResult;
        try {
            userResult = parser.result(
                    transport.post(uri(query), cipher.encrypt(json.writeValueAsString(userLogin))));
        } catch (PandoraApiException e) {
            // Pandora reports bad user credentials with the partner-login code; disambiguate.
            if (e.errorCode() == PandoraErrorCode.INVALID_PARTNER_LOGIN) {
                throw new InvalidLoginException();
            }
            throw e;
        }

        session = new Session(partnerId, timeOffset,
                parser.requiredText(userResult, "userId"),
                parser.requiredText(userResult, "userAuthToken"));
    }

    /** Pandora rejects requests whose clock disagrees with its own, so every call sends corrected time. */
    private long syncTime(long timeOffsetSeconds) {
        return clock.instant().getEpochSecond() - timeOffsetSeconds;
    }

    private URI uri(String query) {
        return URI.create(baseUri + RPC_PATH + "?" + query);
    }

    /** RFC 3986 percent-encoding; auth tokens are base64 and full of {@code + / =}. */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private record Session(String partnerId, long timeOffsetSeconds, String listenerId, String userAuthToken) {
    }
}
