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
import com.tedredington.jazzclub.pandora.model.AccountChange;
import com.tedredington.jazzclub.pandora.model.AccountSettings;
import com.tedredington.jazzclub.pandora.model.AudioEncoding;
import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.pandora.model.GenreCategory;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.SearchResult;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.pandora.model.StationInfo;
import com.tedredington.jazzclub.pandora.model.StationMode;
import com.tedredington.jazzclub.pandora.model.StationSeed;
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
    class Feedback {

        private final Song song = new Song("Peace Piece", "Bill Evans", "Everybody Digs", "tt-1", "300",
                URI.create("https://audio.example/1.m4a"), AudioEncoding.AAC_PLUS, null, null, 0, Duration.ZERO,
                Rating.NONE);

        @Test
        void loveGoesToTheStationTheSongCameFrom() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{}}");

            client.addFeedback(song, true);

            assertThat(transport.request(2).uri().getQuery()).startsWith("method=station.addFeedback&");
            JsonNode body = decryptedBody(2);
            assertThat(body.path("stationToken").asString()).isEqualTo("300");
            assertThat(body.path("trackToken").asString()).isEqualTo("tt-1");
            assertThat(body.path("isPositive").asBoolean()).isTrue();
        }

        @Test
        void banIsNegativeFeedback() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");

            client.addFeedback(song, false);

            assertThat(decryptedBody(2).path("isPositive").asBoolean()).isFalse();
        }

        @Test
        void tiredPutsTheTrackToSleep() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");

            client.sleepSong(song);

            assertThat(transport.request(2).uri().getQuery()).startsWith("method=user.sleepSong&");
            assertThat(decryptedBody(2).path("trackToken").asString()).isEqualTo("tt-1");
        }

        @Test
        void explanationJoinsTraitsIntoASentence() {
            loggedIn();
            transport.respond("""
                    {"stat":"ok","result":{"explanations":[{"focusTraitName":"modal harmonies"},
                    {"focusTraitName":"a piano solo"},{"noTrait":true},{"focusTraitName":"a slow tempo"}]}}""");

            assertThat(client.explain(song)).contains(
                    "We're playing this track because it features modal harmonies, a piano solo and a slow tempo.");
            assertThat(transport.request(2).uri().getQuery()).startsWith("method=track.explainTrack&");
        }

        @Test
        void aClosingTraitThatBringsItsOwnAndIsNotDoubled() {
            loggedIn();
            transport.respond("""
                    {"stat":"ok","result":{"explanations":[{"focusTraitName":"vocal duets"},
                    {"focusTraitName":"country roots"},{"focusTraitName":"and many other similarities"}]}}""");

            assertThat(client.explain(song)).contains("We're playing this track because it features "
                    + "vocal duets, country roots, and many other similarities.");
        }

        @Test
        void aSingleTraitNeedsNoConjunction() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{\"explanations\":[{\"focusTraitName\":\"swing\"}]}}");

            assertThat(client.explain(song)).contains("We're playing this track because it features swing.");
        }

        @Test
        void noExplanationIsEmptyNotAnError() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{\"explanations\":[]}}");

            assertThat(client.explain(song)).isEmpty();
        }

        @Test
        void withRatingChangesOnlyTheRating() {
            assertThat(song.withRating(Rating.LOVE)).isEqualTo(new Song("Peace Piece", "Bill Evans",
                    "Everybody Digs", "tt-1", "300", URI.create("https://audio.example/1.m4a"),
                    AudioEncoding.AAC_PLUS, null, null, 0, Duration.ZERO, Rating.LOVE));
        }
    }

    @Nested
    class StationManagement {

        private final Song song = new Song("So What", "Miles Davis", "Kind of Blue", "tt-9", "200",
                URI.create("https://audio.example/9.m4a"), AudioEncoding.AAC_PLUS, null, null, 0, Duration.ZERO,
                Rating.NONE);

        private String method(int request) {
            return transport.request(request).uri().getQuery().split("&")[0];
        }

        @Test
        void searchFindsArtistsAndSongsWithTheirTokens() {
            loggedIn();
            transport.respond(Fixtures.load("search.json"));

            SearchResult result = client.search("miles");

            assertThat(method(2)).isEqualTo("method=music.search");
            assertThat(decryptedBody(2).path("searchText").asString()).isEqualTo("miles");
            assertThat(result.artists()).containsExactly(
                    new SearchResult.ArtistMatch("Miles Davis", "R123"),
                    new SearchResult.ArtistMatch("Miles Davis Quintet", "R456"));
            assertThat(result.songs()).containsExactly(new SearchResult.SongMatch("So What", "Miles Davis", "S789"));
            assertThat(result.isEmpty()).isFalse();
        }

        @Test
        void aSearchWithoutHitsIsEmptyNotAnError() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{}}");

            assertThat(client.search("zzzz").isEmpty()).isTrue();
        }

        @Test
        void aStationIsCreatedFromASearchTokenAndReturned() {
            loggedIn();
            transport.respond(Fixtures.load("create-station.json"));

            Station created = client.createStation(new StationSeed.MusicToken("R123"));

            assertThat(method(2)).isEqualTo("method=station.createStation");
            assertThat(decryptedBody(2).path("musicToken").asString()).isEqualTo("R123");
            assertThat(decryptedBody(2).has("trackToken")).isFalse();
            assertThat(created).isEqualTo(new Station("500", "Miles Davis Radio", true, false, false));
        }

        @Test
        void aStationFromThePlayingSongOrItsArtistUsesTheTrackToken() {
            loggedIn();
            transport.respond(Fixtures.load("create-station.json")).respond(Fixtures.load("create-station.json"));

            client.createStation(new StationSeed.FromSong(song));
            client.createStation(new StationSeed.FromArtist(song));

            assertThat(decryptedBody(2).path("trackToken").asString()).isEqualTo("tt-9");
            assertThat(decryptedBody(2).path("musicType").asString()).isEqualTo("song");
            assertThat(decryptedBody(3).path("musicType").asString()).isEqualTo("artist");
            assertThat(decryptedBody(3).has("musicToken")).isFalse();
        }

        @Test
        void addMusicRenameDeleteAndTransformNameTheStation() {
            loggedIn();
            for (int i = 0; i < 4; i++) {
                transport.respond("{\"stat\":\"ok\"}");
            }

            client.addMusic(STATION, "R123");
            client.renameStation(STATION, "Late Night");
            client.deleteStation(STATION);
            client.transformSharedStation(STATION);

            assertThat(List.of(method(2), method(3), method(4), method(5))).containsExactly(
                    "method=station.addMusic", "method=station.renameStation",
                    "method=station.deleteStation", "method=station.transformSharedStation");
            assertThat(decryptedBody(2).path("musicToken").asString()).isEqualTo("R123");
            assertThat(decryptedBody(3).path("stationName").asString()).isEqualTo("Late Night");
            for (int request = 2; request <= 5; request++) {
                assertThat(decryptedBody(request).path("stationToken").asString()).isEqualTo("200");
            }
        }

        @Test
        void genreStationsComeGroupedByCategory() {
            loggedIn();
            transport.respond(Fixtures.load("genre-stations.json"));

            List<GenreCategory> categories = client.genreStations();

            assertThat(method(2)).isEqualTo("method=station.getGenreStations");
            assertThat(categories).extracting(GenreCategory::name).containsExactly("Jazz", "Classical");
            assertThat(categories.getFirst().genres()).containsExactly(
                    new GenreCategory.Genre("Bebop", "G100"), new GenreCategory.Genre("Cool Jazz", "G101"));
        }

        @Test
        void quickMixSendsTheMemberIdsAndNeverTheQuickMixItself() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");
            Station quickMix = new Station("100", "QuickMix", true, true, true);

            client.setQuickMix(List.of(quickMix, STATION, new Station("300", "Hard Bop Radio", true, false, true)));

            assertThat(method(2)).isEqualTo("method=user.setQuickMix");
            assertThat(decryptedBody(2).path("quickMixStationIds")).extracting(JsonNode::asString)
                    .containsExactly("200", "300");
        }

        @Test
        void anEmptyQuickMixSelectionIsSentAsAnEmptyList() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");

            client.setQuickMix(List.of());

            assertThat(decryptedBody(2).path("quickMixStationIds").isArray()).isTrue();
            assertThat(decryptedBody(2).path("quickMixStationIds")).isEmpty();
        }

        @Test
        void bookmarksUseTheTrackToken() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}").respond("{\"stat\":\"ok\"}");

            client.bookmarkSong(song);
            client.bookmarkArtist(song);

            assertThat(method(2)).isEqualTo("method=bookmark.addSongBookmark");
            assertThat(method(3)).isEqualTo("method=bookmark.addArtistBookmark");
            assertThat(decryptedBody(3).path("trackToken").asString()).isEqualTo("tt-9");
        }

        @Test
        void stationCopiesChangeExactlyOneProperty() {
            Station shared = new Station("1", "Theirs", false, false, false);

            assertThat(shared.asOwned()).isEqualTo(new Station("1", "Theirs", true, false, false));
            assertThat(shared.withName("Mine")).isEqualTo(new Station("1", "Mine", false, false, false));
            assertThat(shared.withInQuickMix(true)).isEqualTo(new Station("1", "Theirs", false, false, true));
        }
    }

    @Nested
    class SeedsModesAndAccount {

        private String method(int request) {
            return transport.request(request).uri().getQuery().split("&")[0];
        }

        @Test
        void stationInfoListsSeedsAndFeedbackWithTheIdsNeededToDeleteThem() {
            loggedIn();
            transport.respond(Fixtures.load("station-info.json"));

            StationInfo info = client.stationInfo(STATION);

            assertThat(method(2)).isEqualTo("method=station.getStation");
            assertThat(decryptedBody(2).path("stationToken").asString()).isEqualTo("200");
            assertThat(decryptedBody(2).path("includeExtendedAttributes").asBoolean()).isTrue();
            assertThat(info.artistSeeds()).containsExactly(new StationInfo.ArtistSeed("Bill Evans", "A1"));
            assertThat(info.songSeeds()).containsExactly(new StationInfo.SongSeed("So What", "Miles Davis", "S1"),
                    new StationInfo.SongSeed("Naima", "John Coltrane", "S2"));
            assertThat(info.feedback()).containsExactly(
                    new StationInfo.Feedback("Peace Piece", "Bill Evans", "F1", true),
                    new StationInfo.Feedback("Birdland", "Weather Report", "F2", false));
        }

        @Test
        void aBareStationHasNothingToManage() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{\"stationToken\":\"200\"}}");

            StationInfo info = client.stationInfo(STATION);

            assertThat(info.artistSeeds()).isEmpty();
            assertThat(info.songSeeds()).isEmpty();
            assertThat(info.feedback()).isEmpty();
        }

        @Test
        void seedsAndFeedbackAreDeletedByTheirIds() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}").respond("{\"stat\":\"ok\"}");

            client.deleteSeed("S1");
            client.deleteFeedback("F2");

            assertThat(method(2)).isEqualTo("method=station.deleteMusic");
            assertThat(decryptedBody(2).path("seedId").asString()).isEqualTo("S1");
            assertThat(method(3)).isEqualTo("method=station.deleteFeedback");
            assertThat(decryptedBody(3).path("feedbackId").asString()).isEqualTo("F2");
        }

        @Test
        void modesAreListedWithTheActiveOneMarkedAndBrokenEntriesSkipped() {
            loggedIn();
            transport.respond(Fixtures.load("station-modes.json"));

            List<StationMode> modes = client.stationModes(STATION);

            assertThat(method(2)).isEqualTo("method=interactiveradio.v1.getAvailableModesSimple");
            assertThat(decryptedBody(2).path("stationId").asString()).isEqualTo("200");
            assertThat(modes).extracting(StationMode::id).containsExactly(0, 2, 5);
            assertThat(modes).extracting(StationMode::active).containsExactly(false, true, false);
            assertThat(modes.get(1).name()).isEqualTo("Deep Cuts");
        }

        @Test
        void aModeIsSelectedByItsOwnIdNotItsPositionInTheList() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");

            client.setStationMode(STATION, new StationMode(5, "Discovery", "", false));

            assertThat(method(2)).isEqualTo("method=interactiveradio.v1.setAndGetAvailableModes");
            assertThat(decryptedBody(2).path("modeId").asInt()).isEqualTo(5);
        }

        @Test
        void accountSettingsAreRead() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\",\"result\":{\"username\":\"listener@example.com\","
                    + "\"isExplicitContentFilterEnabled\":true,\"zipCode\":\"12345\"}}");

            assertThat(client.accountSettings()).isEqualTo(new AccountSettings("listener@example.com", true));
            assertThat(method(2)).isEqualTo("method=user.getSettings");
        }

        @Test
        void anAccountChangeProvesItselfWithTheCurrentLoginAndSendsOnlyWhatChanges() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}");

            client.changeAccount(AccountChange.NONE.withExplicitContentFilter(true));

            JsonNode body = decryptedBody(2);
            assertThat(method(2)).isEqualTo("method=user.changeSettings");
            assertThat(body.path("currentUsername").asString()).isEqualTo(USER.username());
            assertThat(body.path("currentPassword").asString()).isEqualTo(USER.password());
            assertThat(body.path("userInitiatedChange").asBoolean()).isTrue();
            assertThat(body.path("isExplicitContentFilterEnabled").asBoolean()).isTrue();
            assertThat(body.has("newUsername")).isFalse();
            assertThat(body.has("newPassword")).isFalse();
            assertThat(transport.request(2).body()).as("encrypted like everything else").matches("[0-9a-f]+");
        }

        @Test
        void afterAPasswordChangeTheNextReloginUsesTheNewPassword() {
            loggedIn();
            transport.respond("{\"stat\":\"ok\"}")
                    .respond(Fixtures.failure(1001))
                    .respond(partnerLoginOk())
                    .respond(Fixtures.load("user-login-ok.json"))
                    .respond(Fixtures.load("station-list.json"));

            client.changeAccount(AccountChange.NONE.withPassword("n3w").withUsername("new@example.com"));
            client.stations();

            JsonNode relogin = decryptedBody(5);
            assertThat(relogin.path("username").asString()).isEqualTo("new@example.com");
            assertThat(relogin.path("password").asString()).isEqualTo("n3w");
        }

        @Test
        void aRejectedChangeKeepsTheOldLogin() {
            loggedIn();
            transport.respond(Fixtures.failure(1012))
                    .respond(Fixtures.failure(1001))
                    .respond(partnerLoginOk())
                    .respond(Fixtures.load("user-login-ok.json"))
                    .respond(Fixtures.load("station-list.json"));

            assertThatThrownBy(() -> client.changeAccount(AccountChange.NONE.withPassword("n3w")))
                    .isInstanceOf(PandoraApiException.class);
            client.stations();

            assertThat(decryptedBody(5).path("password").asString()).isEqualTo(USER.password());
        }

        @Test
        void accountChangesNeedALogin() {
            assertThatThrownBy(() -> client.changeAccount(AccountChange.NONE.withPassword("x")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void aNewPasswordNeverShowsUpInToString() {
            AccountChange change = AccountChange.NONE.withPassword("hunter2").withUsername("me");

            assertThat(change.toString()).contains("me").contains("****").doesNotContain("hunter2");
            assertThat(AccountChange.NONE.toString()).contains("newPassword=null");
            assertThat(AccountChange.NONE.isEmpty()).isTrue();
            assertThat(change.isEmpty()).isFalse();
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
