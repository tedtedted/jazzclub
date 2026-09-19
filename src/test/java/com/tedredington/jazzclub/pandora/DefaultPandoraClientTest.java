package com.tedredington.jazzclub.pandora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import com.tedredington.jazzclub.pandora.error.AudioQualityUnavailableException;
import com.tedredington.jazzclub.pandora.error.InvalidLoginException;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.error.PandoraErrorCode;
import com.tedredington.jazzclub.pandora.error.PandoraProtocolException;
import com.tedredington.jazzclub.pandora.model.AudioEncoding;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DefaultPandoraClientTest {

    private static final PartnerCredentials PARTNER = PartnerCredentials.ANDROID;
    private static final UserCredentials USER = new UserCredentials("listener@example.com", "s3cr3t \"pw\"");
    private static final String PARTNER_TOKEN = "VAzrFQTtsy3BQ3K+3iqFi0WF5HA63B1nFA";
    private static final String USER_TOKEN = PARTNER_TOKEN; // same value in the fixture
    private static final String ENCODED_TOKEN = "VAzrFQTtsy3BQ3K%2B3iqFi0WF5HA63B1nFA";

    /** Our clock runs 100 seconds ahead of Pandora's. */
    private static final long LOCAL_TIME = 1_789_855_749L;
    private static final long SERVER_TIME = 1_789_855_649L;
    private static final Station STATION = new Station("200", "Bill Evans Radio", true, false, true);

    /** Plays Pandora's side of the conversation: reads what the client encrypts, writes what it decrypts. */
    private final PandoraCipher serverSide = new PandoraCipher(PARTNER.decryptKey(), PARTNER.encryptKey());
    private final FakeTransport transport = new FakeTransport();
    private final PandoraClient client = new DefaultPandoraClient(transport, PARTNER,
            URI.create("https://tuner.example:443"), InstantSource.fixed(Instant.ofEpochSecond(LOCAL_TIME)));

    private String partnerLoginOk() {
        return """
                {"stat":"ok","result":{"syncTime":"%s","partnerAuthToken":"%s","partnerId":"42"}}
                """.formatted(serverSide.encrypt("\1\2\3\4" + SERVER_TIME), PARTNER_TOKEN);
    }

    private void loggedIn() {
        transport.respond(partnerLoginOk()).respond(Fixtures.load("user-login-ok.json"));
        client.login(USER);
    }

    private JsonNode decryptedBody(int requestIndex) {
        byte[] plain = serverSide.decrypt(transport.request(requestIndex).body());
        return JsonMapper.shared().readTree(new String(plain, StandardCharsets.UTF_8).trim().replace("\0", ""));
    }

    @Nested
    class Login {

        @Test
        void partnerLoginIsSentUnencryptedWithThePartnerCredentials() {
            loggedIn();

            FakeTransport.Request request = transport.request(0);
            assertThat(request.uri()).hasToString("https://tuner.example:443/services/json/?method=auth.partnerLogin");
            JsonNode body = JsonMapper.shared().readTree(request.body());
            assertThat(body.path("username").asString()).isEqualTo("android");
            assertThat(body.path("password").asString()).isEqualTo(PARTNER.password());
            assertThat(body.path("deviceModel").asString()).isEqualTo("android-generic");
            assertThat(body.path("version").asString()).isEqualTo("5");
            assertThat(body.path("includeUrls").asBoolean()).isTrue();
        }

        @Test
        void userLoginIsEncryptedAndCarriesThePartnerTokenInBodyAndUrl() {
            loggedIn();

            assertThat(transport.request(1).uri().toString())
                    .isEqualTo("https://tuner.example:443/services/json/?method=auth.userLogin&auth_token="
                            + ENCODED_TOKEN + "&partner_id=42");
            JsonNode body = decryptedBody(1);
            assertThat(body.path("loginType").asString()).isEqualTo("user");
            assertThat(body.path("username").asString()).isEqualTo(USER.username());
            assertThat(body.path("password").asString()).isEqualTo(USER.password());
            assertThat(body.path("partnerAuthToken").asString()).isEqualTo(PARTNER_TOKEN);
        }

        @Test
        void syncTimeIsCorrectedToPandorasClock() {
            loggedIn();

            assertThat(decryptedBody(1).path("syncTime").asLong()).isEqualTo(SERVER_TIME);
        }

        @Test
        void passwordNeverTravelsInTheClear() {
            loggedIn();

            assertThat(transport.request(1).body()).matches("[0-9a-f]+").doesNotContain("s3cr3t");
            assertThat(transport.request(1).uri().toString()).doesNotContain("s3cr3t");
        }

        @Test
        void wrongUserPasswordIsReportedAsInvalidLoginNotAsPartnerProblem() {
            transport.respond(partnerLoginOk()).respond(Fixtures.failure(1002));

            assertThatThrownBy(() -> client.login(USER))
                    .isInstanceOf(InvalidLoginException.class)
                    .hasMessage("Wrong email address or password.");
        }

        @Test
        void rejectedPartnerLoginKeepsItsOwnErrorCode() {
            transport.respond(Fixtures.failure(1002));

            assertThatThrownBy(() -> client.login(USER))
                    .isInstanceOfSatisfying(PandoraApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(PandoraErrorCode.INVALID_PARTNER_LOGIN));
        }

        @Test
        void otherUserLoginFailuresPassThrough() {
            transport.respond(partnerLoginOk()).respond(Fixtures.failure(12));

            assertThatThrownBy(() -> client.login(USER))
                    .isInstanceOfSatisfying(PandoraApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(PandoraErrorCode.LICENSING_RESTRICTIONS));
        }

        @Test
        void partnerLoginWithoutSyncTimeIsAProtocolError() {
            transport.respond("{\"stat\":\"ok\",\"result\":{\"partnerAuthToken\":\"t\",\"partnerId\":\"1\"}}");

            assertThatThrownBy(() -> client.login(USER))
                    .isInstanceOf(PandoraProtocolException.class)
                    .hasMessageContaining("syncTime");
        }

        @Test
        void callsBeforeLoginAreAProgrammingError() {
            assertThatThrownBy(client::stations).isInstanceOf(IllegalStateException.class);
            assertThat(transport.requests()).isEmpty();
        }

        @Test
        void aFailedLoginLeavesTheClientLoggedOut() {
            transport.respond(partnerLoginOk()).respond(Fixtures.failure(1002));
            assertThatThrownBy(() -> client.login(USER)).isInstanceOf(InvalidLoginException.class);

            assertThatThrownBy(client::stations).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Stations {

        @Test
        void requestCarriesAuthTokenPartnerAndListenerIds() {
            loggedIn();
            transport.respond(Fixtures.load("station-list.json"));

            client.stations();

            assertThat(transport.request(2).uri().toString())
                    .isEqualTo("https://tuner.example:443/services/json/?method=user.getStationList&auth_token="
                            + ENCODED_TOKEN + "&partner_id=42&user_id=1234567");
            JsonNode body = decryptedBody(2);
            assertThat(body.path("returnAllStations").asBoolean()).isTrue();
            assertThat(body.path("userAuthToken").asString()).isEqualTo(USER_TOKEN);
            assertThat(body.path("syncTime").asLong()).isEqualTo(SERVER_TIME);
        }

        @Test
        void parsesStationsAndMarksQuickMixMembers() {
            loggedIn();
            transport.respond(Fixtures.load("station-list.json"));

            List<Station> stations = client.stations();

            assertThat(stations).containsExactly(
                    new Station("100", "QuickMix", true, true, false),
                    new Station("200", "Bill Evans Radio", true, false, true),
                    new Station("300", "Hard Bop Radio", false, false, true),
                    new Station("400", "Thumbprint Radio", true, false, false));
        }

        @Test
        void anAccountWithoutStationsYieldsAnEmptyList() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{}}");

            assertThat(client.stations()).isEmpty();
        }
    }

    @Nested
    class Playlist {

        @Test
        void requestNamesTheStationAndAsksForTrackLength() {
            loggedIn();
            transport.respond(Fixtures.load("playlist.json"));

            client.playlist(STATION, AudioQuality.MEDIUM);

            assertThat(transport.request(2).uri().getQuery()).startsWith("method=station.getPlaylist&");
            JsonNode body = decryptedBody(2);
            assertThat(body.path("stationToken").asString()).isEqualTo("200");
            assertThat(body.path("includeTrackLength").asBoolean()).isTrue();
        }

        @Test
        void parsesSongsAndSkipsAdvertisements() {
            loggedIn();
            transport.respond(Fixtures.load("playlist.json"));

            List<Song> songs = client.playlist(STATION, AudioQuality.MEDIUM);

            assertThat(songs).hasSize(2);
            Song first = songs.getFirst();
            assertThat(first.title()).isEqualTo("Peace Piece");
            assertThat(first.artist()).isEqualTo("Bill Evans");
            assertThat(first.album()).isEqualTo("Everybody Digs Bill Evans");
            assertThat(first.trackToken()).isEqualTo("tt-1");
            assertThat(first.stationId()).isEqualTo("200");
            assertThat(first.audioUrl()).hasToString("https://audio.example/med/1.m4a?token=x");
            assertThat(first.encoding()).isEqualTo(AudioEncoding.AAC_PLUS);
            assertThat(first.coverArtUrl()).hasToString("https://cont.example/art/1.jpg");
            assertThat(first.gainDb()).isEqualTo(-3.21);
            assertThat(first.length()).isEqualTo(Duration.ofSeconds(401));
            assertThat(first.rating()).isEqualTo(Rating.LOVE);
        }

        @Test
        void toleratesMissingOptionalFieldsAndNumericGain() {
            loggedIn();
            transport.respond(Fixtures.load("playlist.json"));

            Song second = client.playlist(STATION, AudioQuality.LOW).get(1);

            assertThat(second.coverArtUrl()).isNull();
            assertThat(second.detailUrl()).isNull();
            assertThat(second.gainDb()).isEqualTo(1.5);
            assertThat(second.length()).isEqualTo(Duration.ZERO);
            assertThat(second.rating()).isEqualTo(Rating.NONE);
        }

        @Test
        void qualitySelectsTheStreamAndItsEncoding() {
            loggedIn();
            transport.respond(Fixtures.load("playlist.json"));

            Song song = client.playlist(STATION, AudioQuality.HIGH).getFirst();

            assertThat(song.audioUrl()).hasToString("https://audio.example/high/1.mp3?token=x");
            assertThat(song.encoding()).isEqualTo(AudioEncoding.MP3);
        }

        @Test
        void aQualityTheAccountDoesNotGetIsReportedClearly() {
            loggedIn();
            transport.respond(Fixtures.load("playlist-no-high-quality.json"));

            assertThatThrownBy(() -> client.playlist(STATION, AudioQuality.HIGH))
                    .isInstanceOfSatisfying(AudioQualityUnavailableException.class,
                            e -> assertThat(e.quality()).isEqualTo(AudioQuality.HIGH))
                    .hasMessageContaining("high");
        }
    }

    @Nested
    class Failures {

        @Test
        void anExpiredTokenTriggersOneReloginAndARetry() {
            loggedIn();
            transport.respond(Fixtures.failure(1001))
                    .respond(partnerLoginOk())
                    .respond(Fixtures.load("user-login-ok.json"))
                    .respond(Fixtures.load("station-list.json"));

            assertThat(client.stations()).hasSize(4);
            assertThat(transport.requests()).extracting(r -> r.uri().getQuery().split("&")[0]).containsExactly(
                    "method=auth.partnerLogin", "method=auth.userLogin",
                    "method=user.getStationList",
                    "method=auth.partnerLogin", "method=auth.userLogin",
                    "method=user.getStationList");
        }

        @Test
        void aTokenThatIsStillInvalidAfterReloginIsNotRetriedForever() {
            loggedIn();
            transport.respond(Fixtures.failure(1001))
                    .respond(partnerLoginOk())
                    .respond(Fixtures.load("user-login-ok.json"))
                    .respond(Fixtures.failure(1001));

            assertThatThrownBy(client::stations)
                    .isInstanceOfSatisfying(PandoraApiException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(PandoraErrorCode.INVALID_AUTH_TOKEN));
            assertThat(transport.requests()).hasSize(6);
        }

        @Test
        void otherApiErrorsAreNotRetried() {
            loggedIn();
            transport.respond(Fixtures.failure(1006));

            assertThatThrownBy(() -> client.playlist(STATION, AudioQuality.LOW))
                    .isInstanceOf(PandoraApiException.class)
                    .hasMessage("Station does not exist.");
            assertThat(transport.requests()).hasSize(3);
        }

        @Test
        void unknownErrorCodesKeepTheirNumberAndServerMessage() {
            loggedIn();
            transport.respond(Fixtures.failure(9999));

            assertThatThrownBy(client::stations)
                    .isInstanceOfSatisfying(PandoraApiException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(PandoraErrorCode.UNKNOWN);
                        assertThat(e.rawCode()).isEqualTo(9999);
                    })
                    .hasMessage("Pandora error 9999: An unexpected error occurred");
        }

        @Test
        void aBodyThatIsNotJsonIsAProtocolError() {
            loggedIn();
            transport.respond("<html>502 Bad Gateway</html>");

            assertThatThrownBy(client::stations).isInstanceOf(PandoraProtocolException.class);
        }

        @Test
        void aResponseWithoutStatusIsAProtocolError() {
            loggedIn();
            transport.respond("{\"result\":{}}");

            assertThatThrownBy(client::stations)
                    .isInstanceOf(PandoraProtocolException.class).hasMessageContaining("status");
        }

        @Test
        void aFailureWithoutCodeIsAProtocolError() {
            loggedIn();
            transport.respond("{\"stat\":\"fail\"}");

            assertThatThrownBy(client::stations)
                    .isInstanceOf(PandoraProtocolException.class).hasMessageContaining("error code");
        }
    }
}
