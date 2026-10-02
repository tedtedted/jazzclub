package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LastFmPropertiesTest {

    private static final URI ENDPOINT = URI.create("https://ws.audioscrobbler.com/2.0/");

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aBlankUserMeansScrobblingIsOff(String user) {
        assertThat(new LastFmProperties(user, "k", "s", ENDPOINT, true).isEnabled()).isFalse();
    }

    @Test
    void anyUserTurnsItOn() {
        assertThat(new LastFmProperties("ted", "k", "s", ENDPOINT, true).isEnabled()).isTrue();
    }

    @Test
    void bothHalvesOfTheApiAccountAreNeeded() {
        assertThat(new LastFmProperties("ted", "k", "s", ENDPOINT, true).hasApiAccount()).isTrue();
        assertThat(new LastFmProperties("ted", "k", " ", ENDPOINT, true).hasApiAccount()).isFalse();
        assertThat(new LastFmProperties("ted", null, "s", ENDPOINT, true).hasApiAccount()).isFalse();
    }
}
