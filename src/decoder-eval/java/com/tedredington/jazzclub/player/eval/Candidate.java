package com.tedredington.jazzclub.player.eval;

import java.util.function.Supplier;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;
import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;

/** The decoders under evaluation (issue #9), each behind jazzclub's own {@link Decoder} interface. */
enum Candidate {

    FFMPEG(() -> FfmpegDecoder.factory("ffmpeg", null, format())),
    LAVAPLAYER(LavaplayerDecoder::factory),
    JAAD(() -> JaadDecoder.factory(format()));

    static final PcmFormat FORMAT = format();

    // a method, because enum constants may not refer to a static field declared after them
    private static PcmFormat format() {
        return PcmFormat.of(44_100);
    }

    private final Supplier<Decoder.Factory> factory;
    private Decoder.Factory created;

    Candidate(Supplier<Decoder.Factory> factory) {
        this.factory = factory;
    }

    synchronized Decoder.Factory factory() {
        if (created == null) {
            created = factory.get();
        }
        return created;
    }
}
