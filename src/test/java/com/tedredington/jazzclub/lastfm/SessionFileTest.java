package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionFileTest {

    @TempDir
    Path directory;

    @Test
    void aSavedSessionIsFoundAgainForTheSameUserInAnyCase() {
        SessionFile file = new SessionFile(directory.resolve("state/lastfm-session"));
        file.save(new Session("Ted", "abc123"));

        assertThat(file.load("ted")).contains(new Session("Ted", "abc123"));
    }

    @Test
    void onlyTheOwnerCanReadIt() throws IOException {
        Path path = directory.resolve("lastfm-session");
        new SessionFile(path).save(new Session("ted", "abc123"));

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path))).isEqualTo("rw-------");
    }

    @Test
    void anotherUsersSessionIsNotUsed() {
        SessionFile file = new SessionFile(directory.resolve("lastfm-session"));
        file.save(new Session("ted", "abc123"));

        assertThat(file.load("someone-else")).isEmpty();
    }

    @Test
    void noFileOrAnIncompleteFileMeansNoSession() throws IOException {
        Path path = directory.resolve("lastfm-session");
        assertThat(new SessionFile(path).load("ted")).isEmpty();

        Files.writeString(path, "user = ted\n");
        assertThat(new SessionFile(path).load("ted")).isEmpty();
    }

    @Test
    void anUnreadableFileMeansSigningInAgainNotACrash() throws IOException {
        Path path = directory.resolve("lastfm-session");
        Files.createDirectory(path); // reading a directory fails

        assertThat(new SessionFile(path).load("ted")).isEmpty();
    }

    @Test
    void deleteForgetsTheSession() {
        SessionFile file = new SessionFile(directory.resolve("lastfm-session"));
        file.save(new Session("ted", "abc123"));
        file.delete();

        assertThat(file.load("ted")).isEmpty();
    }

    @Test
    void failingToSaveIsNotAnError() throws IOException {
        Path blocked = directory.resolve("not-a-directory");
        Files.writeString(blocked, "");

        new SessionFile(blocked.resolve("lastfm-session")).save(new Session("ted", "abc123"));
    }

    @Test
    void theKeyNeverAppearsInToString() {
        assertThat(new Session("ted", "abc123").toString()).doesNotContain("abc123").contains("ted");
    }
}
