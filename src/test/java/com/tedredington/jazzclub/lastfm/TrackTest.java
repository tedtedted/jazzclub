package com.tedredington.jazzclub.lastfm;

import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.tedredington.jazzclub.pandora.model.Song;
import org.junit.jupiter.api.Test;

class TrackTest {

    @Test
    void takesWhatLastFmNeedsFromASong() {
        assertThat(Track.of(song("Nardis", "200")))
                .isEqualTo(new Track("Artist of Nardis", "Nardis", "Album of Nardis", Duration.ofSeconds(185)));
    }

    @Test
    void aBlankAlbumIsLeftOutAndMissingNamesMakeItIncomplete() {
        Song base = song("x", "200");
        Song odd = new Song(null, " ", "  ", base.trackToken(), base.stationId(), base.audioUrl(), base.encoding(),
                null, null, 0, base.length(), base.rating());

        Track track = Track.of(odd);

        assertThat(track.album()).isNull();
        assertThat(track.isComplete()).isFalse();
        assertThat(Track.of(base).isComplete()).isTrue();
    }
}
