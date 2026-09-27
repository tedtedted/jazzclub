package com.tedredington.jazzclub.player.eval;

import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;

/** Native-image entry point that can reach only the ffmpeg candidate; see {@link DecodeTool}. */
public final class FfmpegDecodeTool {

    private FfmpegDecodeTool() {
    }

    public static void main(String[] urls) throws InterruptedException {
        DecodeTool.run("ffmpeg", FfmpegDecoder.factory("ffmpeg", null, DecodeRun.FORMAT), urls);
        System.exit(0);
    }
}
