package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.DecoderType;
import com.tedredington.jazzclub.player.PcmFormat;

class DecoderSelectionTest {

    private final Decoder.Factory builtIn = url -> null;
    private final Decoder.Factory ffmpeg = url -> null;
    private final AtomicBoolean builtInTried = new AtomicBoolean();
    private final List<String> notices = new ArrayList<>();

    private DecoderSelection.BuiltIn working() {
        return () -> {
            builtInTried.set(true);
            return builtIn;
        };
    }

    private Decoder.Factory choose(DecoderType type, int sampleRate, DecoderSelection.BuiltIn builtInDecoder) {
        return DecoderSelection.choose(type, PcmFormat.of(sampleRate), builtInDecoder, () -> ffmpeg, notices::add);
    }

    @Test
    void theBuiltInDecoderIsTheDefaultAndNeedsNoNotice() {
        assertThat(choose(DecoderType.LAVAPLAYER, 0, working())).isSameAs(builtIn);
        assertThat(notices).isEmpty();
    }

    @Test
    void ffmpegWhenConfiguredWithoutTouchingTheBuiltInOneOrSayingSo() {
        assertThat(choose(DecoderType.FFMPEG, 0, working())).isSameAs(ffmpeg);
        assertThat(builtInTried).isFalse();
        assertThat(notices).isEmpty();
    }

    @Test
    void ffmpegWhenTheOutputNeedsResampling() {
        assertThat(choose(DecoderType.LAVAPLAYER, 48_000, working())).isSameAs(ffmpeg);
        assertThat(builtInTried).isFalse();
        assertThat(notices).containsExactly("Decoding with ffmpeg, which resamples to sample_rate 48000.");
    }

    @Test
    void anExplicit44100StaysBuiltIn() {
        assertThat(choose(DecoderType.LAVAPLAYER, 44_100, working())).isSameAs(builtIn);
    }

    @Test
    void ffmpegWhenTheNativeLibraryIsMissingOrDoesNotLoad() {
        DecoderSelection.BuiltIn missing = () -> {
            throw new IOException("No built-in AAC decoder for FreeBSD amd64");
        };
        DecoderSelection.BuiltIn broken = () -> {
            throw new UnsatisfiedLinkError("libstdc++.so.6: cannot open shared object file");
        };

        assertThat(choose(DecoderType.LAVAPLAYER, 0, missing)).isSameAs(ffmpeg);
        assertThat(choose(DecoderType.LAVAPLAYER, 0, broken)).isSameAs(ffmpeg);
        assertThat(notices).containsExactly(
                "Decoding with ffmpeg: the built-in decoder is unavailable "
                        + "(No built-in AAC decoder for FreeBSD amd64).",
                "Decoding with ffmpeg: the built-in decoder is unavailable "
                        + "(libstdc++.so.6: cannot open shared object file).");
    }
}
