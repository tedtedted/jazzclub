package com.tedredington.jazzclub.player.eval;

/** Native-image entry point that can reach only the lavaplayer candidate; see {@link DecodeTool}. */
public final class LavaplayerDecodeTool {

    private LavaplayerDecodeTool() {
    }

    public static void main(String[] urls) throws InterruptedException {
        DecodeTool.run("lavaplayer", LavaplayerDecoder.factory(), urls);
        System.exit(0);
    }
}
