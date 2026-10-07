package com.tedredington.jazzclub.player.javasound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ServiceLoader;
import java.util.Arrays;

import javax.sound.sampled.spi.MixerProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java Sound was never meant to run inside a GraalVM native image. Three things make it work:
 * <ol>
 *   <li>it insists on a {@code java.home} to look for an optional {@code sound.properties};</li>
 *   <li>it finds sound cards through {@link ServiceLoader} with a class computed at runtime, which
 *       native-image cannot see, so the lookup below repeats it with a constant class;</li>
 *   <li>its JNI half, {@code libjsound}, is not linked into the image. The build embeds the library
 *       as a resource; here it is unpacked once into the cache directory, which is then put on
 *       {@code java.library.path}. Loading it ourselves is not enough: the JDK calls
 *       {@code System.loadLibrary("jsound")} itself and only believes in sound if that call succeeds.</li>
 * </ol>
 * On a regular JVM all of this is a no-op.
 */
public final class JavaSoundNativeSupport {

    private static final Logger log = LoggerFactory.getLogger(JavaSoundNativeSupport.class);
    private static final String RESOURCE_ROOT = "/native-libs/";
    private static boolean prepared;

    private JavaSoundNativeSupport() {
    }

    public static synchronized void prepare(Path cacheDirectory) {
        if (prepared) {
            return;
        }
        prepared = true;
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            return;
        }
        if (System.getProperty("java.home") == null) {
            System.setProperty("java.home", System.getProperty("java.io.tmpdir"));
        }
        loadEmbeddedLibrary(cacheDirectory);
        ServiceLoader.load(MixerProvider.class).forEach(provider ->
                log.debug("Sound provider available: {}", provider.getClass().getName()));
    }

    private static void loadEmbeddedLibrary(Path cacheDirectory) {
        String fileName = System.mapLibraryName("jsound");
        String resource = RESOURCE_ROOT + fileName;
        try (InputStream in = JavaSoundNativeSupport.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.debug("No embedded {}; relying on java.library.path", fileName);
                return;
            }
            Files.createDirectories(cacheDirectory);
            Path target = unpack(cacheDirectory, fileName, in.readAllBytes());
            String existing = System.getProperty("java.library.path", "");
            String directory = cacheDirectory.toAbsolutePath().toString();
            System.setProperty("java.library.path",
                    existing.isEmpty() ? directory : directory + java.io.File.pathSeparator + existing);
            log.debug("Unpacked {}", target);
        } catch (IOException e) {
            log.warn("Could not load the sound library; there may be no audio: {}", e.getMessage());
        }
    }

    /** Compare contents: a JDK update can change the library without changing its size. */
    static Path unpack(Path cacheDirectory, String fileName, byte[] bytes) throws IOException {
        Path target = cacheDirectory.resolve(fileName);
        if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
            Path temp = Files.createTempFile(cacheDirectory, fileName, ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return target;
    }
}
