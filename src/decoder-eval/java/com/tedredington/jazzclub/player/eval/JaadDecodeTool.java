package com.tedredington.jazzclub.player.eval;

/** Native-image entry point that can reach only the jaad candidate; see {@link DecodeTool}. */
public final class JaadDecodeTool {

    private JaadDecodeTool() {
    }

    public static void main(String[] urls) throws InterruptedException {
        DecodeTool.run("jaad", JaadDecoder.factory(DecodeRun.FORMAT), urls);
        System.exit(0);
    }
}
