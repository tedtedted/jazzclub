package com.tedredington.jazzclub.player.lavaplayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader;

/**
 * Puts LavaPlayer's native library ({@code libconnector}: fdk-aac and friends) where it can be
 * loaded. Left alone, LavaPlayer copies the library into a new directory under {@code $TMPDIR} on
 * every start and never removes it. Here it is unpacked once into jazzclub's cache directory,
 * rewritten only if it changed, and LavaPlayer is pointed at it.
 */
public final class LavaplayerNatives {

    /** LavaPlayer's switch for "load the connector library from this directory". */
    static final String DIRECTORY_PROPERTY = "lava.native.connector.dir";
    private static final String EMBEDDED = "/native-libs/";

    private static Path prepared;

    private LavaplayerNatives() {
    }

    /**
     * Unpacks the library and loads it, so that a machine it cannot run on is found out here and
     * not on the first song.
     *
     * @throws IOException if this platform has no library, or it cannot be unpacked or loaded; the
     *                     caller can then fall back to ffmpeg
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
        try (InputStream in = open(fileName, resource)) {
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
        try {
            ConnectorNativeLibLoader.loadConnectorLibrary();
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("The built-in AAC decoder does not load here: " + e.getMessage(), e);
        }
        prepared = cacheDirectory;
    }

    /**
     * The native build embeds just this platform's library under {@code /native-libs/} (see the
     * {@code native} profile in pom.xml); on the JVM it comes from the lavaplayer-natives jar.
     */
    private static InputStream open(String fileName, String jarResource) throws IOException {
        InputStream embedded = LavaplayerNatives.class.getResourceAsStream(EMBEDDED + fileName);
        InputStream in = embedded != null ? embedded : LavaplayerNatives.class.getResourceAsStream(jarResource);
        if (in == null) {
            throw new IOException("The built-in AAC decoder is missing from this build (" + jarResource + ")");
        }
        return in;
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
