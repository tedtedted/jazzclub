package com.tedredington.jazzclub.pandora.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AudioQualityTest {

    @Test
    void mapsPianobarConfigValues() {
        assertThat(AudioQuality.fromConfigValue("low")).contains(AudioQuality.LOW);
        assertThat(AudioQuality.fromConfigValue("medium")).contains(AudioQuality.MEDIUM);
        assertThat(AudioQuality.fromConfigValue("high")).contains(AudioQuality.HIGH);
        assertThat(AudioQuality.fromConfigValue("ultra")).isEmpty();
    }

    @Test
    void unknownEncodingsDoNotFailParsing() {
        assertThat(AudioEncoding.fromApiValue("aacplus")).isEqualTo(AudioEncoding.AAC_PLUS);
        assertThat(AudioEncoding.fromApiValue("opus")).isEqualTo(AudioEncoding.UNKNOWN);
        assertThat(AudioEncoding.fromApiValue("")).isEqualTo(AudioEncoding.UNKNOWN);
    }
}
