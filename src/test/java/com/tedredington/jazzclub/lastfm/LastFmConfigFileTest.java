package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Last.fm settings come from the config file like any other, in pianobar's spelling. */
@SpringBootTest(properties = "jazzclub.config-file=src/test/resources/config/sample-config")
class LastFmConfigFileTest {

    @Autowired
    private LastFmProperties properties;

    @Test
    void lastfmLoveZeroTurnsLovesOff() {
        assertThat(properties.love()).isFalse();
    }
}
