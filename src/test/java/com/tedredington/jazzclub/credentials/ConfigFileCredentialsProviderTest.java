package com.tedredington.jazzclub.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tedredington.jazzclub.config.file.ParsedConfig;
import com.tedredington.jazzclub.pandora.UserCredentials;
import org.junit.jupiter.api.Test;

class ConfigFileCredentialsProviderTest {

    private static final Path FILE = Path.of("/home/ted/.config/jazzclub/config");

    private final List<String> commandsRun = new ArrayList<>();
    private String commandOutput = "from-command\n";

    private CredentialsProvider providerFor(Map<String, String> entries) {
        CommandRunner runner = command -> {
            commandsRun.add(command);
            return commandOutput;
        };
        return new ConfigFileCredentialsProvider(() -> new ParsedConfig(entries, List.of()), FILE, runner);
    }

    @Test
    void usesUserAndPlainPassword() {
        assertThat(providerFor(Map.of("user", "me@example.com", "password", "hunter2")).credentials())
                .isEqualTo(new UserCredentials("me@example.com", "hunter2"));
        assertThat(commandsRun).isEmpty();
    }

    @Test
    void passwordsKeepTheirWhitespace() {
        assertThat(providerFor(Map.of("user", " me@example.com ", "password", " spaced ")).credentials())
                .isEqualTo(new UserCredentials("me@example.com", " spaced "));
    }

    @Test
    void plainPasswordWinsOverPasswordCommandLikeInPianobar() {
        providerFor(Map.of("user", "me", "password", "plain", "password_command", "pass show x")).credentials();

        assertThat(commandsRun).isEmpty();
    }

    @Test
    void fallsBackToTheFirstLineOfPasswordCommand() {
        commandOutput = "s3cret\nsecond line is ignored\n";

        UserCredentials credentials = providerFor(Map.of("user", "me", "password_command", "pass show x")).credentials();

        assertThat(credentials.password()).isEqualTo("s3cret");
        assertThat(commandsRun).containsExactly("pass show x");
    }

    @Test
    void anEmptyPlainPasswordAlsoFallsBackToTheCommand() {
        assertThat(providerFor(Map.of("user", "me", "password", "", "password_command", "x")).credentials().password())
                .isEqualTo("from-command");
    }

    @Test
    void aCommandThatPrintsNothingIsAnError() {
        commandOutput = "";

        assertThatThrownBy(() -> providerFor(Map.of("user", "me", "password_command", "true")).credentials())
                .isInstanceOf(CredentialsException.class).hasMessageContaining("printed nothing");
    }

    @Test
    void aMissingUserTellsWhereToAddIt() {
        assertThatThrownBy(() -> providerFor(Map.of("password", "x")).credentials())
                .isInstanceOf(CredentialsException.class)
                .hasMessageContaining("user = ").hasMessageContaining(FILE.toString());
    }

    @Test
    void aMissingPasswordNamesBothOptions() {
        assertThatThrownBy(() -> providerFor(Map.of("user", "me")).credentials())
                .isInstanceOf(CredentialsException.class)
                .hasMessageContaining("password = ").hasMessageContaining("password_command");
    }

    @Test
    void theFileIsReadAgainOnEveryCall() {
        List<Map<String, String>> versions = new ArrayList<>(List.of(
                Map.of("user", "me", "password", "old"), Map.of("user", "me", "password", "new")));
        CredentialsProvider provider = new ConfigFileCredentialsProvider(
                () -> new ParsedConfig(versions.removeFirst(), List.of()), FILE, c -> "");

        assertThat(provider.credentials().password()).isEqualTo("old");
        assertThat(provider.credentials().password()).isEqualTo("new");
    }
}
