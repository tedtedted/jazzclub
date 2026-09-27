package com.tedredington.jazzclub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tedredington.jazzclub.pandora.model.Song;
import com.tedredington.jazzclub.testsupport.TestData;

class SpikeUrlLogTest {

    @TempDir
    Path dir;

    @Test
    void appendsEncodingAndUrlToAnOwnerOnlyFile() throws Exception {
        Path file = dir.resolve("sub/urls.txt");
        SpikeUrlLog urls = SpikeUrlLog.of(file.toString());
        Song first = TestData.song("One", "s1");
        Song second = TestData.song("Two", "s1");

        urls.record(first);
        urls.record(second);

        assertThat(Files.readAllLines(file)).satisfiesExactly(
                line -> assertThat(line).endsWith(" " + first.encoding() + " " + first.audioUrl()),
                line -> assertThat(line).endsWith(" " + second.encoding() + " " + second.audioUrl()));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test
    void doesNothingWhenNoFileIsNamed() {
        SpikeUrlLog.of(null).record(TestData.song("One", "s1"));
        SpikeUrlLog.of(" ").record(TestData.song("One", "s1"));

        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void anUnwritableFileIsNotFatal() throws Exception {
        Path directoryInTheWay = Files.createDirectory(dir.resolve("urls.txt"));

        SpikeUrlLog.of(directoryInTheWay.toString()).record(TestData.song("One", "s1"));

        assertThat(directoryInTheWay).isDirectory();
    }
}
