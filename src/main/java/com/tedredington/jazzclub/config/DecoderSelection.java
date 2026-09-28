package com.tedredington.jazzclub.config;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.DecoderType;
import com.tedredington.jazzclub.player.PcmFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the {@code decoder} setting into a {@link Decoder.Factory}. The built-in decoder is the
 * default; ffmpeg takes over where the built-in one cannot work: its native library does not load
 * on this machine, or {@code sample_rate} asks for resampling, which only ffmpeg does. Either way
 * the user is told, so they know which decoder is playing and why ffmpeg may be needed.
 */
final class DecoderSelection {

    /** The rate Pandora streams at, and the only one the built-in decoder produces. */
    static final int STREAM_RATE = 44_100;

    private static final Logger log = LoggerFactory.getLogger(DecoderSelection.class);

    /** Creates the built-in decoder; throws if its native library is unusable here. */
    @FunctionalInterface
    interface BuiltIn {
        Decoder.Factory create() throws IOException;
    }

    private DecoderSelection() {
    }

    /** @param notice shows one line to the user when ffmpeg stands in for the built-in decoder */
    static Decoder.Factory choose(DecoderType configured, PcmFormat format, BuiltIn builtIn,
                                  Supplier<Decoder.Factory> ffmpeg, Consumer<String> notice) {
        if (configured == DecoderType.FFMPEG) {
            return ffmpeg.get();
        }
        if (format.sampleRate() != STREAM_RATE) {
            return fallBack(ffmpeg, notice, "Decoding with ffmpeg, which resamples to sample_rate "
                    + format.sampleRate() + ".");
        }
        try {
            return builtIn.create();
        } catch (IOException | RuntimeException | LinkageError e) {
            log.debug("Built-in decoder unavailable", e);
            return fallBack(ffmpeg, notice, "Decoding with ffmpeg: the built-in decoder is unavailable ("
                    + e.getMessage() + ").");
        }
    }

    private static Decoder.Factory fallBack(Supplier<Decoder.Factory> ffmpeg, Consumer<String> notice,
                                            String message) {
        log.info(message);
        notice.accept(message);
        return ffmpeg.get();
    }
}
