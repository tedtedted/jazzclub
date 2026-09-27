package com.tedredington.jazzclub.player.eval;

import java.net.URI;
import java.util.Locale;

import com.tedredington.jazzclub.player.Decoder;

/**
 * Command-line decoder for the native-image part of the evaluation (D1/D2 in issue #9). There is
 * one entry point per candidate, so each native binary contains exactly one decoder and the size
 * difference between them is that decoder's cost. It prints one line of measurements per URL,
 * the same on the JVM and in the binary if the binary decodes correctly.
 */
final class DecodeTool {

    private DecodeTool() {
    }

    static void run(String name, Decoder.Factory factory, String[] urls) throws InterruptedException {
        for (String url : urls) {
            DecodeRun run = DecodeRun.of(factory, URI.create(url), 60);
            Pcm pcm = run.pcm();
            String status = run.hung() ? "hung" : run.succeeded() ? "ok" : "error: " + run.reportedError();
            System.out.println(String.format(Locale.ROOT,
                    "%s %s frames=%d level1k=%.2f sbr=%.2f sepL=%.2f sepR=%.2f firstMs=%d totalMs=%d %s",
                    name, url.substring(url.lastIndexOf('/') + 1), pcm.frames(), pcm.toneDb(0, 1000),
                    pcm.bandDb(0, 12000, 15000) - pcm.bandDb(0, 1000, 4000),
                    pcm.toneDb(0, 3000) - pcm.toneDb(0, 5000), pcm.toneDb(1, 5000) - pcm.toneDb(1, 3000),
                    run.firstByteMs(), run.totalMs(), status));
        }
    }
}
