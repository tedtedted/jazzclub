package com.tedredington.jazzclub.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

import com.tedredington.jazzclub.pandora.model.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TEMPORARY, decoder-spike branch only (issue #9): records each song's stream URL so the decoder
 * evaluation can run against real Pandora audio. Off unless {@code JAZZCLUB_SPIKE_URL_LOG} names a
 * file. The URLs grant access to the audio for a while, so the file is created readable by its
 * owner only; keep it out of the repository ({@code target/} is ignored).
 */
final class SpikeUrlLog {

    static final String VARIABLE = "JAZZCLUB_SPIKE_URL_LOG";
    static final SpikeUrlLog FROM_ENVIRONMENT = of(System.getenv(VARIABLE));

    private static final Logger log = LoggerFactory.getLogger(SpikeUrlLog.class);

    private final Path file;

    private SpikeUrlLog(Path file) {
        this.file = file;
    }

    static SpikeUrlLog of(String path) {
        return new SpikeUrlLog(path == null || path.isBlank() ? null : Path.of(path));
    }

    /** Appends {@code <time> <encoding> <url>}; a failure is logged and otherwise ignored. */
    void record(Song song) {
        if (file == null) {
            return;
        }
        String line = Instant.now() + " " + song.encoding() + " " + song.audioUrl() + "\n";
        try {
            if (!Files.exists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.createFile(file);
                try {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException e) {
                    // not a POSIX file system: nothing to restrict
                }
            }
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not record the stream URL in {}: {}", file, e.getMessage());
        }
    }
}
