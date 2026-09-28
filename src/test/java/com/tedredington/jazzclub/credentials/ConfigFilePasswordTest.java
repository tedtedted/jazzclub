package com.tedredington.jazzclub.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.tedredington.jazzclub.config.file.ConfigKey;
import com.tedredington.jazzclub.config.file.ParsedConfig;
import org.junit.jupiter.api.Test;

/** The shared rules, seen through Last.fm's keys; Pandora's are covered by ConfigFileCredentialsProviderTest. */
class ConfigFilePasswordTest {

    private final List<String> commandsRun = new java.util.ArrayList<>();
    private String commandOutput = "s3cret\nsecond line\n";
    private final ConfigFilePassword password = new ConfigFilePassword(
            ConfigKey.LASTFM_PASSWORD, ConfigKey.LASTFM_PASSWORD_COMMAND, command -> {
                commandsRun.add(command);
                return commandOutput;
            });

    private static ParsedConfig config(Map<String, String> entries) {
        return new ParsedConfig(entries, List.of());
    }

    @Test
    void neitherKeyMeansNoPasswordRatherThanAnError() {
        assertThat(password.find(config(Map.of("password", "pandora's")))).isEmpty();
        assertThat(commandsRun).isEmpty();
    }

    @Test
    void thePlainPasswordWins() {
        assertThat(password.find(config(Map.of("lastfm_password", "pw", "lastfm_password_command", "x"))))
                .contains("pw");
        assertThat(commandsRun).isEmpty();
    }

    @Test
    void otherwiseTheFirstLineOfTheCommand() {
        assertThat(password.find(config(Map.of("lastfm_password_command", "pass show last.fm")))).contains("s3cret");
        assertThat(commandsRun).containsExactly("pass show last.fm");
    }

    @Test
    void aCommandPrintingNothingIsNamedInTheError() {
        commandOutput = "";

        assertThatThrownBy(() -> password.find(config(Map.of("lastfm_password_command", "true"))))
                .isInstanceOf(CredentialsException.class)
                .hasMessage("lastfm_password_command printed nothing");
    }
}
