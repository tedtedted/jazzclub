package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LavaplayerNativesTest {

    @TempDir
    Path cache;

    @Test
    void mapsTheReleasedPlatformsToLavaPlayersNames() {
        assertThat(LavaplayerNatives.platform("Mac OS X", "aarch64")).isEqualTo("darwin");
        assertThat(LavaplayerNatives.platform("Mac OS X", "x86_64")).isEqualTo("darwin");
        assertThat(LavaplayerNatives.platform("Linux", "amd64")).isEqualTo("linux-x86-64");
        assertThat(LavaplayerNatives.platform("Linux", "aarch64")).isEqualTo("linux-aarch64");
    }

    @Test
    void knowsNoOtherPlatforms() {
        assertThat(LavaplayerNatives.platform("Linux", "riscv64")).isNull();
        assertThat(LavaplayerNatives.platform("Windows 11", "amd64")).isNull();
        assertThat(LavaplayerNatives.platform("FreeBSD", "amd64")).isNull();
    }

    /** Other tests may have prepared first; either way the library must end up where LavaPlayer looks. */
    @Test
    void unpacksTheLibraryOnceAndPointsLavaPlayerAtIt() throws Exception {
        LavaplayerNatives.prepare(cache);
        LavaplayerNatives.prepare(cache);

        Path directory = Path.of(System.getProperty(LavaplayerNatives.DIRECTORY_PROPERTY));
        assertThat(directory.resolve(System.mapLibraryName("connector"))).isRegularFile();
        try (var files = Files.list(directory)) {
            assertThat(files.filter(f -> f.toString().endsWith(".tmp"))).isEmpty();
        }
    }
}
