package com.tedredington.jazzclub.player.lavaplayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts LavaPlayer's native library ({@code libconnector}: fdk-aac and friends) where it can be
 * loaded, in this order:
 * <ol>
 *   <li>installed next to the binary, as the packages do: {@code <bin>/../lib/jazzclub/} (deb,
 *       AUR), {@code <bin>/../libexec/jazzclub/} (macOS bundle), {@code <bin>/../libexec/} (a
 *       Homebrew keg's private libexec) or the binary's own directory. It is used where it is;
 *       nothing is written.</li>
 *   <li>otherwise the copy embedded in the build, unpacked into jazzclub's cache directory and
 *       rewritten only if it changed. This keeps {@code java -jar} and a bare downloaded binary
 *       working.</li>
 * </ol>
 * Left alone, LavaPlayer would instead copy the library into a new directory under {@code $TMPDIR}
 * on every start and never remove it.
 */
public final class LavaplayerNatives {

    /** LavaPlayer's switch for "load the connector library from this directory". */
    static final String DIRECTORY_PROPERTY = "lava.native.connector.dir";
    private static final String EMBEDDED = "/native-libs/";
    private static final String FILE_NAME = System.mapLibraryName("connector");
    private static final Logger log = LoggerFactory.getLogger(LavaplayerNatives.class);

    private static Path prepared;

    private LavaplayerNatives() {
    }

    /**
     * Finds or unpacks the library and loads it, so that a machine it cannot run on is found out
     * here and not on the first song.
     *
     * @throws IOException if there is no library for this platform, or it cannot be unpacked or
     *                     loaded; the caller can then fall back to ffmpeg
     */
    public static synchronized void prepare(Path cacheDirectory) throws IOException {
        if (prepared != null) {
            return;
        }
        Path directory = locate(ProcessHandle.current().info().command().map(Path::of), cacheDirectory);
        System.setProperty(DIRECTORY_PROPERTY, directory.toAbsolutePath().toString());
        try {
            ConnectorNativeLibLoader.loadConnectorLibrary();
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("The built-in AAC decoder does not load here: " + e.getMessage(), e);
        }
        prepared = directory;
    }

    /** The directory to load the library from: the installed copy, else the unpacked one. */
    static Path locate(Optional<Path> executable, Path cacheDirectory) throws IOException {
        Optional<Path> installed = executable.flatMap(LavaplayerNatives::installedDirectory);
        if (installed.isPresent()) {
            log.debug("Using the installed {} in {}", FILE_NAME, installed.get());
            return installed.get();
        }
        unpack(cacheDirectory);
        log.debug("Using {} unpacked into {}", FILE_NAME, cacheDirectory);
        return cacheDirectory;
    }

    /**
     * Where a package put the library, relative to the binary. Symlinks are resolved first:
     * Homebrew runs {@code bin/jazzclub} through a link from its prefix into the keg, and the
     * library sits in the keg.
     */
    static Optional<Path> installedDirectory(Path executable) {
        Path binary;
        try {
            binary = executable.toRealPath();
        } catch (IOException e) {
            binary = executable.toAbsolutePath();
        }
        Path bin = binary.getParent();
        if (bin == null) {
            return Optional.empty();
        }
        Path prefix = bin.getParent();
        List<Path> candidates = prefix == null
                ? List.of(bin)
                : List.of(prefix.resolve("lib").resolve("jazzclub"), prefix.resolve("libexec").resolve("jazzclub"),
                        prefix.resolve("libexec"), bin);
        return candidates.stream().filter(directory -> Files.isRegularFile(directory.resolve(FILE_NAME))).findFirst();
    }

    private static void unpack(Path cacheDirectory) throws IOException {
        String platform = platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        if (platform == null) {
            throw new IOException("No built-in AAC decoder for " + System.getProperty("os.name") + " "
                    + System.getProperty("os.arch"));
        }
        try (InputStream in = open("/natives/" + platform + "/" + FILE_NAME)) {
            byte[] bytes = in.readAllBytes();
            Files.createDirectories(cacheDirectory);
            Path target = cacheDirectory.resolve(FILE_NAME);
            if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                Path temp = Files.createTempFile(cacheDirectory, FILE_NAME, ".tmp");
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        }
    }

    /**
     * The native build embeds just this platform's library under {@code /native-libs/} (see the
     * {@code native} profile in pom.xml); on the JVM it comes from the lavaplayer-natives jar.
     */
    private static InputStream open(String jarResource) throws IOException {
        InputStream embedded = LavaplayerNatives.class.getResourceAsStream(EMBEDDED + FILE_NAME);
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
