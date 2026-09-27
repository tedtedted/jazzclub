package com.tedredington.jazzclub.player.lavaplayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * Puts LavaPlayer's native library ({@code libconnector}: fdk-aac and friends) where it can be
 * loaded. Left alone, LavaPlayer copies the library into a new directory under {@code $TMPDIR} on
 * every start and never removes it. Here it is unpacked once into jazzclub's cache directory,
 * rewritten only if it changed, and LavaPlayer is pointed at it.
 */
public final class LavaplayerNatives {

    /** LavaPlayer's switch for "load the connector library from this directory". */
    static final String DIRECTORY_PROPERTY = "lava.native.connector.dir";

    private static Path prepared;

    private LavaplayerNatives() {
    }

    /**
     * @throws IOException if this platform has no library or it cannot be unpacked; the caller can
     *                     then fall back to ffmpeg
     */
    public static synchronized void prepare(Path cacheDirectory) throws IOException {
        if (prepared != null) {
            return;
        }
        String platform = platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        if (platform == null) {
            throw new IOException("No built-in AAC decoder for " + System.getProperty("os.name") + " "
                    + System.getProperty("os.arch"));
        }
        String fileName = System.mapLibraryName("connector");
        String resource = "/natives/" + platform + "/" + fileName;
        try (InputStream in = LavaplayerNatives.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("The built-in AAC decoder is missing from this build (" + resource + ")");
            }
            byte[] bytes = in.readAllBytes();
            Files.createDirectories(cacheDirectory);
            Path target = cacheDirectory.resolve(fileName);
            if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                Path temp = Files.createTempFile(cacheDirectory, fileName, ".tmp");
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        }
        System.setProperty(DIRECTORY_PROPERTY, cacheDirectory.toAbsolutePath().toString());
        prepared = cacheDirectory;
    }

    /** LavaPlayer's name for the platforms jazzclub is released for, or {@code null}. */
    static String platform(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("linux")) {
            return switch (arch) {
                case "amd64", "x86_64" -> "linux-x86-64";
                case "aarch64", "arm64" -> "linux-aarch64";
                default -> null;
            };
        }
        return null;
    }
}
