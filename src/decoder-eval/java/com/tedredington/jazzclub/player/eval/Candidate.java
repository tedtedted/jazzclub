package com.tedredington.jazzclub.player.eval;

import java.util.function.Supplier;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;

/** The decoders under evaluation (issue #9), each behind jazzclub's own {@link Decoder} interface. */
enum Candidate {

    FFMPEG(() -> FfmpegDecoder.factory("ffmpeg", null, DecodeRun.FORMAT)),
    LAVAPLAYER(LavaplayerDecoder::factory),
    JAAD(() -> JaadDecoder.factory(DecodeRun.FORMAT));

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
