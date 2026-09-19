package com.tedredington.jazzclub.config.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ConfigKeyTest {

    @Test
    void credentialsAreSecretsAndHaveNoSpringProperty() {
        assertThat(Arrays.stream(ConfigKey.values()).filter(ConfigKey::isSecret))
                .containsExactlyInAnyOrder(ConfigKey.USER, ConfigKey.PASSWORD, ConfigKey.PASSWORD_COMMAND);
    }

    @Test
    void everyOtherKeyMapsIntoTheJazzclubNamespace() {
        Arrays.stream(ConfigKey.values()).filter(k -> !k.isSecret())
                .forEach(k -> assertThat(k.property()).startsWith("jazzclub."));
    }

    @Test
    void looksUpByPianobarName() {
        assertThat(ConfigKey.fromFileKey("audio_quality")).isEqualTo(ConfigKey.AUDIO_QUALITY);
        assertThat(ConfigKey.fromFileKey("act_songlove")).isNull();
    }
}
