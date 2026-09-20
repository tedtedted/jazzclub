package com.tedredington.jazzclub.pandora;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.error.InvalidLoginException;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraErrorCode;
import com.tedredington.jazzclub.pandora.model.AccountChange;
import com.tedredington.jazzclub.pandora.model.AccountSettings;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationInfo;
import com.tedredington.jazzclub.pandora.model.StationMode;
import com.tedredington.jazzclub.pandora.model.StationSeed;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
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

    @Override
    public synchronized void addFeedback(Song song, boolean positive) {
        ObjectNode body = json.createObjectNode()
                .put("stationToken", song.stationId())
                .put("trackToken", song.trackToken())
                .put("isPositive", positive);
        call("station.addFeedback", body);
    }

    @Override
    public synchronized void sleepSong(Song song) {
        call("user.sleepSong", json.createObjectNode().put("trackToken", song.trackToken()));
    }

    @Override
    public synchronized Optional<String> explain(Song song) {
        ObjectNode body = json.createObjectNode().put("trackToken", song.trackToken());
        return parser.explanation(call("track.explainTrack", body));
    }

    @Override
    public synchronized SearchResult search(String text) {
        return parser.searchResult(call("music.search", json.createObjectNode().put("searchText", text)));
    }

    @Override
    public synchronized Station createStation(StationSeed seed) {
        ObjectNode body = json.createObjectNode();
        switch (seed) {
            case StationSeed.MusicToken(String token) -> body.put("musicToken", token);
            case StationSeed.FromSong(Song song) -> body.put("trackToken", song.trackToken()).put("musicType", "song");
            case StationSeed.FromArtist(Song song) ->
                    body.put("trackToken", song.trackToken()).put("musicType", "artist");
        }
        return parser.station(call("station.createStation", body));
    }

    @Override
    public synchronized void addMusic(Station station, String musicToken) {
        call("station.addMusic", json.createObjectNode()
                .put("musicToken", musicToken)
                .put("stationToken", station.token()));
    }

    @Override
    public synchronized void renameStation(Station station, String newName) {
        call("station.renameStation", json.createObjectNode()
                .put("stationToken", station.token())
                .put("stationName", newName));
    }

    @Override
    public synchronized void deleteStation(Station station) {
        call("station.deleteStation", stationToken(station));
    }

    @Override
    public synchronized List<GenreCategory> genreStations() {
        return parser.genreCategories(call("station.getGenreStations", json.createObjectNode()));
    }

    @Override
    public synchronized void setQuickMix(Collection<Station> members) {
        ObjectNode body = json.createObjectNode();
        ArrayNode ids = body.putArray("quickMixStationIds");
        // the QuickMix station cannot contain itself
        members.stream().filter(s -> !s.quickMix()).map(Station::token).forEach(ids::add);
        call("user.setQuickMix", body);
    }

    @Override
    public synchronized void transformSharedStation(Station station) {
        call("station.transformSharedStation", stationToken(station));
    }

    @Override
    public synchronized void bookmarkSong(Song song) {
        call("bookmark.addSongBookmark", json.createObjectNode().put("trackToken", song.trackToken()));
    }

    @Override
    public synchronized void bookmarkArtist(Song song) {
        call("bookmark.addArtistBookmark", json.createObjectNode().put("trackToken", song.trackToken()));
    }

    @Override
    public synchronized StationInfo stationInfo(Station station) {
        ObjectNode body = stationToken(station)
                .put("includeExtendedAttributes", true)
                .put("includeExtraParams", true);
        return parser.stationInfo(call("station.getStation", body));
    }

    @Override
    public synchronized void deleteSeed(String seedId) {
        call("station.deleteMusic", json.createObjectNode().put("seedId", seedId));
    }

    @Override
    public synchronized void deleteFeedback(String feedbackId) {
        call("station.deleteFeedback", json.createObjectNode().put("feedbackId", feedbackId));
    }

    @Override
    public synchronized List<StationMode> stationModes(Station station) {
        return parser.stationModes(call("interactiveradio.v1.getAvailableModesSimple",
                json.createObjectNode().put("stationId", station.token())));
    }

    @Override
    public synchronized void setStationMode(Station station, StationMode mode) {
        // pianobar sends the position in the list here; the mode's own id is what Pandora asks for
        call("interactiveradio.v1.setAndGetAvailableModes", json.createObjectNode()
                .put("stationId", station.token())
                .put("modeId", mode.id()));
    }

    @Override
    public synchronized AccountSettings accountSettings() {
        return parser.accountSettings(call("user.getSettings", json.createObjectNode()));
    }

    @Override
    public synchronized void changeAccount(AccountChange change) {
        if (credentials == null) {
            throw new IllegalStateException("login() must succeed before calling user.changeSettings");
        }
        ObjectNode body = json.createObjectNode()
                .put("userInitiatedChange", true)
                .put("currentUsername", credentials.username())
                .put("currentPassword", credentials.password());
        if (change.explicitContentFilter() != null) {
            body.put("isExplicitContentFilterEnabled", change.explicitContentFilter());
        }
        if (change.newUsername() != null) {
            body.put("newUsername", change.newUsername());
        }
        if (change.newPassword() != null) {
            body.put("newPassword", change.newPassword());
        }
        call("user.changeSettings", body);
        // a later re-login, e.g. after the token expired, must use what is valid now
        credentials = new UserCredentials(
                change.newUsername() != null ? change.newUsername() : credentials.username(),
                change.newPassword() != null ? change.newPassword() : credentials.password());
    }

    private ObjectNode stationToken(Station station) {
        return json.createObjectNode().put("stationToken", station.token());
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
