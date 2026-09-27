package com.tedredington.jazzclub.player.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.eval.FixtureServer.Behaviour;

/**
 * The automated part of the decoder evaluation in issue #9. Each test is one row of the report
 * and runs once per candidate. Check IDs (C1, B2, ...) match the issue.
 *
 * <p>Fixtures play a -12 dBFS sine (amplitude 0.25), so a correct decode measures about -12 dB.
 */
class DecoderEvaluationTest {

    private static final double TONE_DB = 20 * Math.log10(0.25);
    private static final long TIMEOUT_SECONDS = 20;

    private static FixtureServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = new FixtureServer();
    }

    @AfterAll
    static void writeReport() {
        server.close();
        EvalReport.note("Tones are -12.0 dBFS in the source. Noise is white, so every band should match the 1-4 kHz band.");
        EvalReport.note("Java: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        noteJarSize("LavaPlayer", com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager.class);
        noteJarSize("LavaPlayer natives (all platforms)",
                String.valueOf(ClassLoader.getSystemResource("natives/linux-x86-64/libconnector.so")));
        noteJarSize("vavi-sound-aac", net.sourceforge.jaad.aac.Decoder.class);
        EvalReport.write(Path.of("target", "decoder-eval", "report.md"));
    }

    // --- C: correctness ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void c1LowComplexityBaseline(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, "lc-1k.m4a");
        double level = run.pcm().toneDb(0, 1000);
        boolean passed = run.succeeded() && Math.abs(level - TONE_DB) <= 1 && Math.abs(run.pcm().seconds() - 3) <= 0.15;
        check("C1 AAC-LC 1 kHz (level, length)", candidate, passed,
                run.succeeded() ? db(level) + ", " + secs(run) : error(run));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void c2ExplicitSbr(Candidate candidate) throws Exception {
        sbrCheck("C2 HE-AAC explicit SBR, M4A: 12-15 kHz vs 1-4 kHz", candidate, "he-noise.m4a");
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void c3ImplicitSbr(Candidate candidate) throws Exception {
        sbrCheck("C3 HE-AAC implicit SBR, ADTS: 12-15 kHz vs 1-4 kHz", candidate, "he-noise.aac");
    }

    /** White noise is flat: with SBR the high band matches the core's level, without it it is gone. */
    private void sbrCheck(String name, Candidate candidate, String fixture) throws Exception {
        DecodeRun run = decode(candidate, fixture);
        double high = run.pcm().bandDb(0, 12000, 15000) - run.pcm().bandDb(0, 1000, 4000);
        boolean passed = run.succeeded() && high >= -6 && Math.abs(run.pcm().seconds() - 8) <= 0.15;
        check(name, candidate, passed, run.succeeded() ? db(high) + ", " + secs(run) : error(run));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void c4ParametricStereo(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, "hev2-panned.m4a");
        Pcm pcm = run.pcm();
        double left = pcm.toneDb(0, 3000) - pcm.toneDb(0, 5000);
        double right = pcm.toneDb(1, 5000) - pcm.toneDb(1, 3000);
        boolean passed = run.succeeded() && left >= 10 && right >= 10;
        check("C4 HE-AACv2 stereo separation (L 3 kHz, R 5 kHz)", candidate, passed,
                run.succeeded() ? "L " + db(left) + ", R " + db(right) : error(run));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void c5BandResponse(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, "he-noise.m4a");
        double reference = run.pcm().bandDb(0, 1000, 4000);
        double worst = 0;
        String worstBand = "";
        StringBuilder levels = new StringBuilder();
        int[][] bands = {{100, 300}, {300, 1000}, {4000, 8000}, {8000, 11000}, {11000, 13000}, {13000, 15000},
                {15000, 16000}};
        for (int[] band : bands) {
            double deviation = run.pcm().bandDb(0, band[0], band[1]) - reference;
            String label = khz(band[0]) + "-" + khz(band[1]);
            if (band[1] <= 15000 && Math.abs(deviation) > Math.abs(worst)) {
                worst = deviation;
                worstBand = label;
            }
            levels.append(label).append(' ').append(db(deviation)).append(", ");
        }
        boolean passed = run.succeeded() && Math.abs(worst) <= 3;
        check("C5 HE-AAC white noise, band level vs 1-4 kHz (to 15 kHz)", candidate, passed,
                run.succeeded() ? "worst " + db(worst) + " at " + worstBand + " (" + levels.substring(0,
                        levels.length() - 2) + ")" : error(run));
    }

    // --- B: robustness ----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b1ConnectionDroppedOnce(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, server.mount("he-noise.m4a", Behaviour.NORMAL.droppingOnceAfter(6000)));
        boolean complete = run.pcm().seconds() >= 7.9;
        // recovering is best; reporting the error is acceptable; a short track and "success" is not
        boolean passed = !run.hung() && (complete || run.reportedError() != null);
        check("B1a connection drops once mid-stream", candidate, passed,
                run.hung() ? "hung" : (complete ? "recovered, " : "no recovery, ") + secs(run)
                        + (run.reportedError() != null ? ", error: " + run.reportedError() : ", no error reported"));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b1ServerDiesMidStream(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, server.mount("he-noise.m4a", Behaviour.NORMAL.dyingAfter(6000)));
        boolean passed = !run.hung() && run.reportedError() != null;
        check("B1b server dies mid-stream", candidate, passed, outcome(run));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b2Truncated(Candidate candidate) throws Exception {
        brokenInput("B2 truncated file (16 KB of 60 KB)", candidate, "truncated.m4a");
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b2Empty(Candidate candidate) throws Exception {
        brokenInput("B2 empty file", candidate, "empty.m4a");
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b2Garbage(Candidate candidate) throws Exception {
        brokenInput("B2 random bytes", candidate, "garbage.m4a");
    }

    private void brokenInput(String name, Candidate candidate, String fixture) throws Exception {
        DecodeRun run = decode(candidate, fixture);
        check(name, candidate, !run.hung() && run.reportedError() != null, outcome(run));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b3CloseFromAnotherThread(Candidate candidate) throws Exception {
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(8000));
        Decoder decoder;
        try {
            decoder = candidate.factory().open(url);
        } catch (IOException e) {
            check("B3 close() while a read is blocked", candidate, false, "open failed: " + e);
            return;
        }
        CountDownLatch firstByte = new CountDownLatch(1);
        CountDownLatch readerDone = new CountDownLatch(1);
        AtomicLong returnedAt = new AtomicLong();
        Thread reader = Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream pcm = decoder.pcm()) {
                while (pcm.read(buffer) >= 0) {
                    firstByte.countDown();
                }
            } catch (IOException e) {
                // an exception ends the read as well as -1 does
            } finally {
                returnedAt.set(System.nanoTime());
                readerDone.countDown();
            }
        });
        boolean started = firstByte.await(10, TimeUnit.SECONDS);
        Thread.sleep(300);
        if (readerDone.getCount() == 0) {
            check("B3 close() while a read is blocked", candidate, false,
                    "decoding finished before close(): the whole throttled file was read before the first PCM");
            return;
        }
        long closedAt = System.nanoTime();
        decoder.close();
        boolean returned = readerDone.await(5, TimeUnit.SECONDS);
        long ms = TimeUnit.NANOSECONDS.toMillis(returnedAt.get() - closedAt);
        reader.interrupt();
        check("B3 close() while a read is blocked", candidate, started && returned && ms <= 1000,
                !started ? "no audio before close" : returned ? "read returned after " + ms + " ms" : "read still blocked after 5 s");
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b4MoovAtEndWithoutRanges(Candidate candidate) throws Exception {
        // flaky for ffmpeg in the first runs, so it is repeated and counted
        int runs = 5;
        int played = 0;
        String lastFailure = "";
        for (int i = 0; i < runs; i++) {
            DecodeRun run = decode(candidate, server.mount("he-noise-moov-last.m4a", Behaviour.NORMAL.withoutRanges()));
            if (run.succeeded() && Math.abs(run.pcm().seconds() - 8) <= 0.15) {
                played++;
            } else {
                lastFailure = outcome(run);
            }
        }
        check("B4 MP4 index at the end, server without range support (5 runs)", candidate, played == runs,
                "played " + played + "/" + runs + (played < runs ? "; last failure: " + lastFailure : ""));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void b4MoovAtEndWithRanges(Candidate candidate) throws Exception {
        DecodeRun run = decode(candidate, server.mount("he-noise-moov-last.m4a"));
        boolean passed = run.succeeded() && Math.abs(run.pcm().seconds() - 8) <= 0.15;
        check("B4 MP4 index at the end, server with range support", candidate, passed,
                run.succeeded() ? "plays, " + secs(run) : outcome(run));
    }

    // --- S: streaming -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void s1TimeToFirstPcm(Candidate candidate) throws Exception {
        decode(candidate, "he-noise.m4a"); // warm up the JVM and the factory
        URI url = server.mount("he-noise.m4a");
        DecodeRun run = decode(candidate, url);
        check("S1 time to first PCM (warm)", candidate, run.succeeded() && run.firstByteMs() < 1000,
                run.succeeded() ? run.firstByteMs() + " ms" : error(run));
        check("S3 HTTP requests for one track", candidate, true, requests(url));
    }

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void s2ThrottledLink(Candidate candidate) throws Exception {
        // 24 KB/s is about 3x the fixture's 60 KB over 8 s
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(24000));
        DecodeRun run = decode(candidate, url);
        boolean passed = run.succeeded() && run.pcm().seconds() >= 7.9;
        check("S2 slow link (24 KB/s): first PCM, total", candidate, passed,
                run.succeeded() ? run.firstByteMs() + " ms, " + run.totalMs() + " ms" : outcome(run));
        check("S3 HTTP requests, MP4 index at end", candidate, true,
                requests(runAndMount(candidate, "he-noise-moov-last.m4a")));
    }

    // --- E: performance ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Candidate.class)
    void e1DecodeSpeed(Candidate candidate) throws Exception {
        long best = Long.MAX_VALUE;
        DecodeRun run = null;
        for (int i = 0; i < 5; i++) {
            run = decode(candidate, "he-noise.m4a");
            best = Math.min(best, run.totalMs());
        }
        double realtime = run.pcm().seconds() / Math.max(best, 1) * 1000;
        check("E1 decode speed, best of 5 (8 s HE-AAC)", candidate, run.succeeded() && realtime >= 20,
                run.succeeded() ? String.format(Locale.ROOT, "%.0fx real time (%d ms)", realtime, best) : error(run));
    }

    // --- helpers ----------------------------------------------------------------------------

    private static DecodeRun decode(Candidate candidate, String fixture) throws InterruptedException {
        return decode(candidate, server.mount(fixture));
    }

    private static DecodeRun decode(Candidate candidate, URI url) throws InterruptedException {
        return DecodeRun.of(candidate.factory(), url, TIMEOUT_SECONDS);
    }

    private static URI runAndMount(Candidate candidate, String fixture) throws InterruptedException {
        URI url = server.mount(fixture);
        decode(candidate, url);
        return url;
    }

    private static void check(String name, Candidate candidate, boolean passed, String measurement) {
        EvalReport.record(name, candidate, passed, measurement);
        assertThat(passed).as("%s: %s", name, measurement).isTrue();
    }

    private static String requests(URI url) {
        var requests = server.requestsFor(url);
        return requests.size() + ": " + requests.stream()
                .map(r -> r.status() + (r.range() != null ? " " + r.range() : ""))
                .toList();
    }

    private static String outcome(DecodeRun run) {
        if (run.hung()) {
            return "hung (" + TIMEOUT_SECONDS + " s)";
        }
        return (run.reportedError() != null ? "error: " + run.reportedError() : "no error reported") + ", "
                + secs(run) + " of audio";
    }

    private static String error(DecodeRun run) {
        return run.hung() ? "hung" : "error: " + run.reportedError();
    }

    private static String db(double value) {
        return String.format(Locale.ROOT, "%+.1f dB", value);
    }

    private static String khz(int hz) {
        return hz >= 1000 ? hz / 1000 + "k" : String.valueOf(hz);
    }

    private static String secs(DecodeRun run) {
        return String.format(Locale.ROOT, "%.3f s", run.pcm().seconds());
    }

    private static void noteJarSize(String label, Class<?> type) {
        noteJarSize(label, type.getProtectionDomain().getCodeSource().getLocation().toString());
    }

    /** @param location a file: URL, or a jar: URL of something inside the jar */
    private static void noteJarSize(String label, String location) {
        try {
            String file = location.startsWith("jar:") ? location.substring(4, location.indexOf("!/")) : location;
            Path jar = Path.of(URI.create(file));
            EvalReport.note(String.format(Locale.ROOT, "%s jar: %s, %.1f MB", label, jar.getFileName(),
                    Files.size(jar) / 1_048_576.0));
        } catch (Exception e) {
            EvalReport.note(label + " jar size unknown: " + e);
        }
    }
}
