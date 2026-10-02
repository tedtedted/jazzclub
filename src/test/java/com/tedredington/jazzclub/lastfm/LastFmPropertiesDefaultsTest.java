package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LastFmPropertiesDefaultsTest {

    @Autowired
    private LastFmProperties properties;

    @Test
    void lovesAreOnUnlessTurnedOff() {
        assertThat(properties.love()).isTrue();
    }
}
