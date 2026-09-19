package com.tedredington.jazzclub.config.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserConfigFileTest {

    @TempDir
    Path directory;

    private final UserConfigFile userConfigFile = new UserConfigFile();

    private Path write(String content, String permissions) throws IOException {
        Path file = directory.resolve("config");
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
        return file;
    }

    @Test
    void aMissingFileIsNotAnError() {
        assertThat(userConfigFile.load(directory.resolve("absent"))).isEqualTo(ParsedConfig.EMPTY);
    }

    @Test
    void readsEntries() throws IOException {
        Path file = write("user = me@example.com\naudio_quality = low\n", "rw-------");

        ParsedConfig config = userConfigFile.load(file);

        assertThat(config.entries()).containsEntry("user", "me@example.com").containsEntry("audio_quality", "low");
        assertThat(config.warnings()).isEmpty();
    }

    @Test
    void warnsWhenAPlainTextPasswordIsReadableByOthers() throws IOException {
        Path file = write("user = me\npassword = hunter2\n", "rw-r--r--");

        assertThat(userConfigFile.load(file).warnings()).singleElement().asString()
                .contains("readable by other users")
                .contains("chmod 600 " + file)
                .doesNotContain("hunter2");
    }

    @Test
    void aGroupReadableFileIsAlsoFlagged() throws IOException {
        assertThat(userConfigFile.load(write("password = x\n", "rw-r-----")).warnings()).hasSize(1);
    }

    @Test
    void aPrivateFileWithAPasswordIsFine() throws IOException {
        assertThat(userConfigFile.load(write("password = x\n", "rw-------")).warnings()).isEmpty();
    }

    @Test
    void permissionsDoNotMatterWithoutAPlainTextPassword() throws IOException {
        Path file = write("user = me\npassword_command = pass show pandora\n", "rw-r--r--");

        assertThat(userConfigFile.load(file).warnings()).isEmpty();
    }

    @Test
    void parserWarningsArePassedOn() throws IOException {
        assertThat(userConfigFile.load(write("nonsense\n", "rw-------")).warnings())
                .singleElement().asString().contains("Invalid line").contains(":1");
    }

    @Test
    void anUnreadablePathIsReportedWithItsLocation() {
        // a directory cannot be read as a file
        assertThatThrownBy(() -> userConfigFile.load(directory))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(directory.toString());
    }
}
