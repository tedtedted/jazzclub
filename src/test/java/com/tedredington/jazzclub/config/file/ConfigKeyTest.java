package com.tedredington.jazzclub.config.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ConfigKeyTest {

    @Test
    void credentialsAreSecretsAndHaveNoSpringProperty() {
        assertThat(Arrays.stream(ConfigKey.values()).filter(ConfigKey::isSecret))
                .containsExactlyInAnyOrder(ConfigKey.USER, ConfigKey.PASSWORD, ConfigKey.PASSWORD_COMMAND,
                        ConfigKey.LASTFM_PASSWORD, ConfigKey.LASTFM_PASSWORD_COMMAND);
    }

    @Test
    void everyOtherKeyMapsIntoTheJazzclubNamespace() {
        Arrays.stream(ConfigKey.values()).filter(k -> !k.isSecret())
                .forEach(k -> assertThat(k.property()).startsWith("jazzclub."));
    }

    @Test
    void settingsThatNameAFileExpandTheHomeShorthandLikePianobar() {
        assertThat(ConfigKey.FIFO.resolve("~/.config/jazzclub/ctl", "/home/ted")).isEqualTo("/home/ted/.config/jazzclub/ctl");
        assertThat(ConfigKey.AUDIO_PIPE.resolve("~/snapfifo", "/home/ted")).isEqualTo("/home/ted/snapfifo");
        assertThat(ConfigKey.EVENT_COMMAND.resolve("/usr/bin/x", "/home/ted")).isEqualTo("/usr/bin/x");
        assertThat(ConfigKey.FIFO.resolve("~other/ctl", "/home/ted")).as("only the own home").isEqualTo("~other/ctl");
    }

    @Test
    void otherSettingsAreLeftAloneEvenIfTheyStartWithATilde() {
        assertThat(ConfigKey.LOVE_ICON.resolve("~/<3", "/home/ted")).isEqualTo("~/<3");
    }

    @Test
    void looksUpByPianobarName() {
        assertThat(ConfigKey.fromFileKey("audio_quality")).isEqualTo(ConfigKey.AUDIO_QUALITY);
        assertThat(ConfigKey.fromFileKey("act_songlove")).isNull();
    }
}
