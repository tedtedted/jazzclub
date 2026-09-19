package com.tedredington.jazzclub.config.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the user's config file from disk. A missing file is normal and yields an empty config. */
public final class UserConfigFile {

    private static final Set<PosixFilePermission> READABLE_BY_OTHERS = Set.of(
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);

    private final ConfigFileParser parser = new ConfigFileParser();

    public ParsedConfig load(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return ParsedConfig.EMPTY;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + file, e);
        }

        ParsedConfig parsed = parser.parse(lines, file.toString());
        List<String> warnings = new ArrayList<>(parsed.warnings());
        if (parsed.get("password").isPresent() && isReadableByOthers(file)) {
            warnings.add(file + " contains your password and is readable by other users. Fix with: chmod 600 "
                    + file + "  (or use password_command instead)");
        }
        return new ParsedConfig(parsed.entries(), warnings);
    }

    private static boolean isReadableByOthers(Path file) {
        try {
            return Files.getPosixFilePermissions(file).stream().anyMatch(READABLE_BY_OTHERS::contains);
        } catch (IOException | UnsupportedOperationException e) {
            return false; // not a POSIX file system; nothing sensible to check
        }
    }
}
