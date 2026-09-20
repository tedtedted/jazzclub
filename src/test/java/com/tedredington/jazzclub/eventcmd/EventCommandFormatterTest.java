package com.tedredington.jazzclub.eventcmd;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.pandora.error.PandoraApiException;
import com.tedredington.jazzclub.pandora.model.AudioEncoding;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import org.junit.jupiter.api.Test;

/** The expected text is pianobar's format, field for field and in its order; existing scripts rely on it. */
class EventCommandFormatterTest {

    private static final Song PEACE_PIECE = new Song("Peace Piece", "Bill Evans", "Everybody Digs Bill Evans",
            "tt-1", "200", URI.create("https://audio.example/1.m4a"), AudioEncoding.AAC_PLUS,
            URI.create("https://art.example/1.jpg"), URI.create("https://www.pandora.com/peace-piece"), -3.2,
            Duration.ofSeconds(401), Rating.LOVE);
    private static final Song NEXT = new Song("So What", "Miles Davis", "Kind of Blue", "tt-2", "200",
            URI.create("https://audio.example/2.m4a"), AudioEncoding.AAC_PLUS, null, null, 0,
            Duration.ofSeconds(562), Rating.NONE);

    @Test
    void aSongEventLooksExactlyLikePianobars() {
        PlayerEvent event = new PlayerEvent(EventType.SONG_FINISH, EventResult.OK, QUICKMIX, PEACE_PIECE, EVANS,
                Duration.ofSeconds(399), List.of(NEXT), List.of(EVANS, HARD_BOP, QUICKMIX));

        assertThat(EventCommandFormatter.format(event)).isEqualTo("""
                stationName=QuickMix
                songStationName=Bill Evans Radio
                pRet=1
                pRetStr=Everything is fine :)
                wRet=0
                wRetStr=No error
                songPlayed=399
                artist=Bill Evans
                title=Peace Piece
                album=Everybody Digs Bill Evans
                coverArt=https://art.example/1.jpg
                rating=1
                detailUrl=https://www.pandora.com/peace-piece
                songDuration=401
                artistNext0=Miles Davis
                titleNext0=So What
                albumNext0=Kind of Blue
                coverArtNext0=
                ratingNext0=0
                detailUrlNext0=
                songDurationNext0=562
                stationCount=3
                station0=Bill Evans Radio
                station1=Hard Bop Radio
                station2=QuickMix
                """);
    }

    @Test
    void anEventWithoutSongOrStationStillHasEveryFixedField() {
        PlayerEvent event = new PlayerEvent(EventType.USER_LOGIN, EventResult.of(new PandoraApiException(1002, null)),
                null, null, null, Duration.ZERO, List.of(), List.of());

        assertThat(EventCommandFormatter.format(event)).isEqualTo("""
                stationName=
                songStationName=
                pRet=2026
                pRetStr=Invalid partner login.
                wRet=0
                wRetStr=No error
                songPlayed=0
                stationCount=0
                """);
    }

    @Test
    void ratingsUsePianobarsNumbers() {
        for (Rating rating : Rating.values()) {
            PlayerEvent event = new PlayerEvent(EventType.SONG_START, EventResult.OK, EVANS,
                    PEACE_PIECE.withRating(rating), null, Duration.ZERO, List.of(), List.of());
            int expected = switch (rating) {
                case NONE -> 0;
                case LOVE -> 1;
                case BAN -> 2;
                case TIRED -> 3;
            };
            assertThat(EventCommandFormatter.format(event)).contains("\nrating=" + expected + "\n");
        }
    }

    @Test
    void aLineBreakInATitleCannotForgeAnotherField() {
        Song sneaky = new Song("Title\npRet=0", "Artist\r\nx", "Album", "t", "200", PEACE_PIECE.audioUrl(),
                AudioEncoding.AAC_PLUS, null, null, 0, Duration.ZERO, Rating.NONE);
        PlayerEvent event = new PlayerEvent(EventType.SONG_START, EventResult.OK, EVANS, sneaky, null, Duration.ZERO,
                List.of(), List.of());

        String text = EventCommandFormatter.format(event);

        assertThat(text).contains("title=Title pRet=0\n").contains("artist=Artist  x\n");
        assertThat(text.lines().filter(line -> line.startsWith("pRet="))).containsExactly("pRet=1");
    }
}
