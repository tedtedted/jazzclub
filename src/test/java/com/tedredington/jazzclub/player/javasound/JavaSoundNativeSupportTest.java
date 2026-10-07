package com.tedredington.jazzclub.player.javasound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSoundNativeSupportTest {

    @TempDir
    Path cache;

    @Test
    void replacesChangedLibraryEvenWhenItsSizeIsUnchanged() throws Exception {
        Path target = cache.resolve("library");
        Files.write(target, new byte[] {1, 2, 3});

        JavaSoundNativeSupport.unpack(cache, "library", new byte[] {4, 5, 6});

        assertThat(Files.readAllBytes(target)).containsExactly(4, 5, 6);
        try (var files = Files.list(cache)) {
            assertThat(files.toList()).containsExactly(target);
        }
    }

    @Test
    void leavesUnchangedLibraryInPlace() throws Exception {
        Path target = JavaSoundNativeSupport.unpack(cache, "library", new byte[] {1, 2, 3});
        FileTime previous = FileTime.fromMillis(1_000);
        Files.setLastModifiedTime(target, previous);

        JavaSoundNativeSupport.unpack(cache, "library", new byte[] {1, 2, 3});

        assertThat(Files.getLastModifiedTime(target)).isEqualTo(previous);
    }
}
