package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LavaplayerNativesTest {

    private static final String LIBRARY = System.mapLibraryName("connector");

    @TempDir
    Path cache;

    @TempDir
    Path install;

    /** A package's layout: {@code <prefix>/bin/jazzclub} plus the library wherever given. */
    private Path binaryWithLibraryIn(String... directories) throws Exception {
        Path binary = Files.createDirectories(install.resolve("bin")).resolve("jazzclub");
        Files.writeString(binary, "");
        for (String directory : directories) {
            Files.writeString(Files.createDirectories(install.resolve(directory)).resolve(LIBRARY), "fake");
        }
        return binary;
    }

    @Test
    void prefersLibJazzclubThenLibexecThenTheBinarysOwnDirectory() throws Exception {
        Path binary = binaryWithLibraryIn("lib/jazzclub", "libexec", "bin");
        assertThat(LavaplayerNatives.installedDirectory(binary))
                .contains(install.resolve("lib/jazzclub").toRealPath());

        Files.delete(install.resolve("lib/jazzclub").resolve(LIBRARY));
        assertThat(LavaplayerNatives.installedDirectory(binary))
                .contains(install.resolve("libexec").toRealPath());

        Files.delete(install.resolve("libexec").resolve(LIBRARY));
        assertThat(LavaplayerNatives.installedDirectory(binary))
                .contains(install.resolve("bin").toRealPath());

        Files.delete(install.resolve("bin").resolve(LIBRARY));
        assertThat(LavaplayerNatives.installedDirectory(binary)).isEmpty();
    }

    /** Homebrew links {@code <prefix>/bin/jazzclub} to the keg, where the library is. */
    @Test
    void followsASymlinkToTheRealBinary() throws Exception {
        Path binary = binaryWithLibraryIn("libexec");
        Path link = Files.createDirectories(cache.resolve("elsewhere/bin")).resolve("jazzclub");
        Files.createSymbolicLink(link, binary);

        assertThat(LavaplayerNatives.installedDirectory(link))
                .contains(install.resolve("libexec").toRealPath());
    }

    @Test
    void usesTheInstalledCopyWithoutWritingToTheCache() throws Exception {
        Path binary = binaryWithLibraryIn("lib/jazzclub");
        Path unusedCache = cache.resolve("lib");

        Path directory = LavaplayerNatives.locate(Optional.of(binary), unusedCache);

        assertThat(directory).isEqualTo(install.resolve("lib/jazzclub").toRealPath());
        assertThat(unusedCache).doesNotExist();
    }

    @Test
    void unpacksIntoTheCacheWhenNothingIsInstalled() throws Exception {
        Path binary = binaryWithLibraryIn();

        Path directory = LavaplayerNatives.locate(Optional.of(binary), cache);

        assertThat(directory).isEqualTo(cache);
        assertThat(cache.resolve(LIBRARY)).isRegularFile().isNotEmptyFile();
        assertThat(LavaplayerNatives.locate(Optional.empty(), cache)).isEqualTo(cache);
    }

    /** prepare() relies on this, also inside a native image (-PnativeTest runs it there). */
    @Test
    void theRunningExecutableIsKnown() {
        assertThat(ProcessHandle.current().info().command()).hasValueSatisfying(
                command -> assertThat(Path.of(command)).isAbsolute().exists());
    }

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
