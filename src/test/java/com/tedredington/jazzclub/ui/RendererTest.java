package com.tedredington.jazzclub.ui;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.FORMAT;
import static com.tedredington.jazzclub.testsupport.TestData.HARD_BOP;
import static com.tedredington.jazzclub.testsupport.TestData.QUICKMIX;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.tedredington.jazzclub.config.JazzclubProperties;
import com.tedredington.jazzclub.pandora.model.Rating;
import com.tedredington.jazzclub.pandora.model.Song;
import org.junit.jupiter.api.Test;

class RendererTest {

    private final Renderer renderer = new Renderer(FORMAT);
    private final Song song = song("Peace Piece", "200");

    @Test
    void nowPlayingSongUsesPianobarsDefaultLayout() {
        assertThat(renderer.nowPlayingSong(song, null))
                .isEqualTo("\"Peace Piece\" by \"Artist of Peace Piece\" on \"Album of Peace Piece\"");
    }

    @Test
    void ratingsShowTheirIcon() {
        assertThat(renderer.nowPlayingSong(song.withRating(Rating.LOVE), null)).endsWith("\" <3");
        assertThat(renderer.nowPlayingSong(song.withRating(Rating.BAN), null)).endsWith("\" </3");
        assertThat(renderer.nowPlayingSong(song.withRating(Rating.TIRED), null)).endsWith("\" zZ");
    }

    @Test
    void ratingIconsAreAvailableOnTheirOwn() {
        assertThat(renderer.ratingIcon(Rating.LOVE)).isEqualTo(" <3");
        assertThat(renderer.ratingIcon(Rating.NONE)).isEmpty();
    }

    @Test
    void quickMixNamesTheStationTheSongCameFrom() {
        assertThat(renderer.nowPlayingSong(song, EVANS)).endsWith("\" @ Bill Evans Radio");
    }

    @Test
    void stationLineShowsNameAndId() {
        assertThat(renderer.nowPlayingStation(EVANS)).isEqualTo("Station \"Bill Evans Radio\" (200)");
    }

    @Test
    void listEntriesAreNumberedAndRightAligned() {
        assertThat(renderer.listSong(3, song, null)).isEqualTo(" 3) Artist of Peace Piece - Peace Piece");
        assertThat(renderer.listSong(12, song.withRating(Rating.LOVE), null)).startsWith("12) ").endsWith(" <3");
    }

    @Test
    void customFormatsCanUseDurationStationAndElapsedTime() {
        Renderer custom = new Renderer(new JazzclubProperties.Format(
                "%t %u", "%n", "%i) %t (%d)%@%s", "%e/%t", "+", "-", "z", " @ ", java.util.Map.of()));

        assertThat(custom.listSong(0, song, "Hard Bop Radio")).isEqualTo(" 0) Peace Piece (03:05) @ Hard Bop Radio");
        assertThat(custom.listSong(0, song, null)).isEqualTo(" 0) Peace Piece (03:05)");
        assertThat(custom.time(Duration.ofSeconds(65), Duration.ofSeconds(185))).isEqualTo("01:05/03:05");
        assertThat(custom.nowPlayingSong(song, null)).isEqualTo("Peace Piece ");
    }

    @Test
    void unknownDurationIsShownAsQuestionMarks() {
        Song noLength = new Song("t", "a", "l", "tok", "1", song.audioUrl(), song.encoding(), null, null, 0,
                Duration.ZERO, Rating.NONE);
        Renderer custom = new Renderer(new JazzclubProperties.Format("", "", "%d", "", "", "", "", "", java.util.Map.of()));

        assertThat(custom.listSong(0, noLength, null)).isEqualTo("??:??");
    }

    @Test
    void timeCountsDownAndFlipsSignOnOverrun() {
        assertThat(renderer.time(Duration.ofSeconds(5), Duration.ofSeconds(185))).isEqualTo("-03:00/03:05");
        assertThat(renderer.time(Duration.ofSeconds(190), Duration.ofSeconds(185))).isEqualTo("+00:05/03:05");
    }

    @Test
    void stationPickerFlagsQuickMixMembershipAndSharing() {
        assertThat(renderer.stationListEntry(0, EVANS)).isEqualTo(" 0) q   Bill Evans Radio");
        assertThat(renderer.stationListEntry(1, QUICKMIX)).isEqualTo(" 1)  Q  QuickMix");
        assertThat(renderer.stationListEntry(10, HARD_BOP)).isEqualTo("10)   S Hard Bop Radio");
    }
}
