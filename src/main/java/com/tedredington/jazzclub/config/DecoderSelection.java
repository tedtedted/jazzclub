package com.tedredington.jazzclub.config;

import java.io.IOException;
import java.util.function.Supplier;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.DecoderType;
import com.tedredington.jazzclub.player.PcmFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the {@code decoder} setting into a {@link Decoder.Factory}. The built-in decoder is the
 * default; ffmpeg takes over, with a warning, where the built-in one cannot work: its native
 * library does not load on this machine, or {@code sample_rate} asks for resampling, which only
 * ffmpeg does.
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

    static Decoder.Factory choose(DecoderType configured, PcmFormat format, BuiltIn builtIn,
                                  Supplier<Decoder.Factory> ffmpeg) {
        if (configured == DecoderType.FFMPEG) {
            return ffmpeg.get();
        }
        if (format.sampleRate() != STREAM_RATE) {
            log.warn("sample_rate {} needs resampling, which only ffmpeg does; decoding with ffmpeg",
                    format.sampleRate());
            return ffmpeg.get();
        }
        try {
            return builtIn.create();
        } catch (IOException | RuntimeException | LinkageError e) {
            log.warn("The built-in decoder is unavailable ({}); decoding with ffmpeg", e.getMessage());
            return ffmpeg.get();
        }
    }
}
