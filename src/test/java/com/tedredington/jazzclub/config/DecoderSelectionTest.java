package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.DecoderType;
import com.tedredington.jazzclub.player.PcmFormat;

class DecoderSelectionTest {

    private final Decoder.Factory builtIn = url -> null;
    private final Decoder.Factory ffmpeg = url -> null;
    private final AtomicBoolean builtInTried = new AtomicBoolean();

    private DecoderSelection.BuiltIn working() {
        return () -> {
            builtInTried.set(true);
            return builtIn;
        };
    }

    @Test
    void theBuiltInDecoderIsTheDefault() {
        assertThat(DecoderSelection.choose(DecoderType.LAVAPLAYER, PcmFormat.of(0), working(), () -> ffmpeg))
                .isSameAs(builtIn);
    }

    @Test
    void ffmpegWhenConfiguredWithoutTouchingTheBuiltInOne() {
        assertThat(DecoderSelection.choose(DecoderType.FFMPEG, PcmFormat.of(0), working(), () -> ffmpeg))
                .isSameAs(ffmpeg);
        assertThat(builtInTried).isFalse();
    }

    @Test
    void ffmpegWhenTheOutputNeedsResampling() {
        assertThat(DecoderSelection.choose(DecoderType.LAVAPLAYER, PcmFormat.of(48_000), working(), () -> ffmpeg))
                .isSameAs(ffmpeg);
        assertThat(builtInTried).isFalse();
    }

    @Test
    void anExplicit44100StaysBuiltIn() {
        assertThat(DecoderSelection.choose(DecoderType.LAVAPLAYER, PcmFormat.of(44_100), working(), () -> ffmpeg))
                .isSameAs(builtIn);
    }

    @Test
    void ffmpegWhenTheNativeLibraryIsMissingOrDoesNotLoad() {
        DecoderSelection.BuiltIn missing = () -> {
            throw new IOException("No built-in AAC decoder for FreeBSD amd64");
        };
        DecoderSelection.BuiltIn broken = () -> {
            throw new UnsatisfiedLinkError("libstdc++.so.6: cannot open shared object file");
        };

        assertThat(DecoderSelection.choose(DecoderType.LAVAPLAYER, PcmFormat.of(0), missing, () -> ffmpeg))
                .isSameAs(ffmpeg);
        assertThat(DecoderSelection.choose(DecoderType.LAVAPLAYER, PcmFormat.of(0), broken, () -> ffmpeg))
                .isSameAs(ffmpeg);
    }
}
