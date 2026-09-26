package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScrobbleRulesTest {

    @ParameterizedTest(name = "{0}s long, {1}s heard: {2}")
    @CsvSource({
            // half the length is enough
            "200, 100, true",
            "200, 99, false",
            // four minutes is enough for long tracks
            "1200, 240, true",
            "1200, 239, false",
            // 30 seconds or less never counts, however much was heard
            "30, 30, false",
            "31, 16, true",
            "31, 15, false",
            // unknown length
            "0, 500, false",
    })
    void lastFmsDefinitionOfAListen(long lengthSeconds, long playedSeconds, boolean counts) {
        assertThat(ScrobbleRules.counts(Duration.ofSeconds(lengthSeconds), Duration.ofSeconds(playedSeconds)))
                .isEqualTo(counts);
    }

    @ParameterizedTest
    @CsvSource({"61, 30500, true", "61, 30499, false"})
    void halfIsExactToTheMillisecond(long lengthSeconds, long playedMillis, boolean counts) {
        assertThat(ScrobbleRules.counts(Duration.ofSeconds(lengthSeconds), Duration.ofMillis(playedMillis)))
                .isEqualTo(counts);
    }
}
