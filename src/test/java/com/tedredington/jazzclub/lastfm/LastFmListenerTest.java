package com.tedredington.jazzclub.lastfm;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.pandora.error.PandoraTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Sequences of player events, through a real Scrobbler, down to what Last.fm would be sent. */
@Timeout(10)
class LastFmListenerTest {

    private static final Instant START = Instant.parse("2026-09-26T20:00:00Z");
    /** 185 seconds long, so 92.5 seconds count. */
    private static final Song NARDIS = song("Nardis", "200");

    @TempDir
    Path directory;

    private final FakeLastFmClient client = new FakeLastFmClient();
    private Instant now = START;
    private LastFmListener listener;

    @BeforeEach
    void setUp() {
        SessionFile sessions = new SessionFile(directory.resolve("lastfm-session"));
        sessions.save(new Session("ted", "sk"));
        Scrobbler scrobbler = new Scrobbler(client, "ted", Optional::empty, sessions, notice -> { },
                Path.of("config"), Duration.ofSeconds(5));
        InstantSource clock = () -> now;
        listener = new LastFmListener(scrobbler, clock);
    }

    private void publish(EventType type, Song song, Duration played) {
        listener.on(new PlayerEvent(type, EventResult.OK, EVANS, song, null, played, List.of(), List.of(EVANS)));
    }

    private List<String> calls() {
        listener.close(); // waits for the worker
        return client.calls;
    }

    @Test
    void aSongHeardLongEnoughIsNowPlayingThenScrobbledUnderItsStartTime() {
        publish(EventType.SONG_START, NARDIS, Duration.ZERO);
        now = START.plusSeconds(200); // including a pause
        publish(EventType.SONG_FINISH, NARDIS, Duration.ofSeconds(93));

        assertThat(calls()).containsExactly("nowPlaying sk Nardis", "scrobble sk Nardis");
        assertThat(client.scrobbles).singleElement().extracting(Scrobble::startedAt).isEqualTo(START);
    }

    @Test
    void skippingEarlyIsNoScrobble() {
        publish(EventType.SONG_START, NARDIS, Duration.ZERO);
        publish(EventType.SONG_FINISH, NARDIS, Duration.ofSeconds(92));

        assertThat(calls()).containsExactly("nowPlaying sk Nardis");
    }

    @Test
    void aLateBanStillCountsAsAListen() {
        Song banned = NARDIS.withRating(Rating.BAN);
        publish(EventType.SONG_START, NARDIS, Duration.ZERO);
        publish(EventType.SONG_BAN, banned, Duration.ofSeconds(150));
        publish(EventType.SONG_FINISH, banned, Duration.ofSeconds(150));

        assertThat(calls()).containsExactly("nowPlaying sk Nardis", "scrobble sk Nardis");
    }

    @Test
    void aSongFinishingWithoutAStartIsDatedBackByWhatWasHeard() {
        now = START.plusSeconds(100);
        publish(EventType.SONG_FINISH, NARDIS, Duration.ofSeconds(100));

        assertThat(calls()).containsExactly("scrobble sk Nardis");
        assertThat(client.scrobbles).singleElement().extracting(Scrobble::startedAt).isEqualTo(START);
    }

    @Test
    void theStartOfAnotherSongIsNotUsed() {
        publish(EventType.SONG_START, song("Other", "200"), Duration.ZERO);
        now = START.plusSeconds(500);
        publish(EventType.SONG_FINISH, NARDIS, Duration.ofSeconds(100));

        calls();
        assertThat(client.scrobbles).singleElement().extracting(Scrobble::startedAt)
                .isEqualTo(START.plusSeconds(400));
    }

    @Test
    void failedEventsAndEventsWithoutASongAreIgnored() {
        listener.on(new PlayerEvent(EventType.SONG_START, EventResult.of(new PandoraTransportException("x", null)),
                EVANS, NARDIS, null, Duration.ZERO, List.of(), List.of()));
        publish(EventType.SONG_START, null, Duration.ZERO);
        publish(EventType.STATION_FETCH_PLAYLIST, null, Duration.ZERO);

        assertThat(calls()).isEmpty();
    }

    @Test
    void songsWithoutArtistOrTitleAreNotSent() {
        Song nameless = new Song("", "", null, "t", "200", NARDIS.audioUrl(), NARDIS.encoding(), null, null, 0,
                NARDIS.length(), Rating.NONE);
        publish(EventType.SONG_START, nameless, Duration.ZERO);
        publish(EventType.SONG_FINISH, nameless, Duration.ofSeconds(185));

        assertThat(calls()).isEmpty();
    }

    @Test
    void withoutAScrobblerNothingHappensAndClosingIsFine() {
        LastFmListener off = new LastFmListener(null, () -> now);

        off.on(new PlayerEvent(EventType.SONG_START, EventResult.OK, EVANS, NARDIS, null, Duration.ZERO, List.of(),
                List.of()));
        off.close();

        assertThat(calls()).isEmpty();
    }
}
