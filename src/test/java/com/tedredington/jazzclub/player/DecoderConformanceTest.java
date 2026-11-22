package com.tedredington.jazzclub.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.tedredington.jazzclub.player.ffmpeg.FfmpegDecoder;
import com.tedredington.jazzclub.player.lavaplayer.LavaplayerDecoder;
import com.tedredington.jazzclub.player.lavaplayer.LavaplayerNatives;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Behaviour;
import com.tedredington.jazzclub.testsupport.PcmAnalysis;

/**
 * What every {@code decoder} setting must do, run against each of them over the synthetic fixtures
 * (see issue #9 for how each was chosen). Tones are -12 dBFS; the noise is white, so every band
 * should match the 1-4 kHz band.
 *
 * <p>Where ffmpeg is not installed its half is skipped, unless {@code JAZZCLUB_REQUIRE_FFMPEG=true}
 * (as in CI), which turns a missing ffmpeg into a failure.
 */
@Timeout(30)
class DecoderConformanceTest {

    private static final double TONE_DB = 20 * Math.log10(0.25);
    private static final PcmFormat FORMAT = PcmFormat.of(0);
    /**
     * Seconds a decode may run over the source: LavaPlayer's MP4 parser ignores the edit list, so
     * the encoder's priming and the SBR delay stay in as ~60 ms of leading silence. ffmpeg trims it.
     */
    private static final double UNTRIMMED_PRIMING = 0.25;

    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private static AudioFixtureServer server;
    private static Boolean ffmpegInstalled;

    private record Result(PcmAnalysis pcm, String failure) {
    }

    @BeforeAll
    static void start() throws IOException {
        server = new AudioFixtureServer();
        LavaplayerNatives.prepare(Files.createTempDirectory("jazzclub-natives"));
    }

    @AfterAll
    static void stop() {
        server.close();
        watchdog.shutdownNow();
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void decodesAacLowComplexity(DecoderType type) throws Exception {
        Result result = decode(type, server.mount("lc-1k.m4a"));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().toneDb(0, 1000)).isCloseTo(TONE_DB, within(1.0));
        assertThat(result.pcm().seconds()).isBetween(3.0, 3.0 + UNTRIMMED_PRIMING);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void decodesTheSbrBandOfHeAac(DecoderType type) throws Exception {
        Result result = decode(type, server.mount("he-noise.m4a"));

        assertThat(result.failure()).isNull();
        // without SBR there is nothing above ~11 kHz; with it, white noise stays flat
        assertThat(result.pcm().bandDb(12000, 15000) - result.pcm().bandDb(1000, 4000)).isGreaterThan(-6);
        assertThat(result.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void keepsParametricStereoApart(DecoderType type) throws Exception {
        Result result = decode(type, server.mount("hev2-panned.m4a"));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().toneDb(0, 3000) - result.pcm().toneDb(0, 5000)).isGreaterThan(10);
        assertThat(result.pcm().toneDb(1, 5000) - result.pcm().toneDb(1, 3000)).isGreaterThan(10);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void playsAFileWithItsIndexAtTheEnd(DecoderType type) throws Exception {
        Result result = decode(type, server.mount("he-noise-moov-last.m4a"));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void resumesAfterTheConnectionDrops(DecoderType type) throws Exception {
        Result result = decode(type, server.mount("he-noise.m4a", Behaviour.NORMAL.droppingOnceAfter(20_000)));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void reportsAServerThatDiesInsteadOfEndingTheSongEarly(DecoderType type) throws Exception {
        assertThat(decode(type, server.mount("he-noise.m4a", Behaviour.NORMAL.dyingAfter(20_000))).failure())
                .isNotNull();
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void reportsATruncatedFile(DecoderType type) throws Exception {
        assertThat(decode(type, server.mount("truncated.m4a")).failure()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void reportsFilesThatAreNotAudio(DecoderType type) throws Exception {
        assertThat(decode(type, server.mount("garbage.m4a")).failure()).isNotNull();
        assertThat(decode(type, server.mount("empty.m4a")).failure()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void reportsHttpErrors(DecoderType type) throws Exception {
        URI missing = server.mount("lc-1k.m4a").resolve("/no-such-file.m4a");

        assertThat(decode(type, missing).failure()).contains("404");
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void refusesAnythingButHttp(DecoderType type) {
        Decoder.Factory decoders = decoders(type);

        assertThatThrownBy(() -> decoders.open(URI.create("file:///etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    void closeUnblocksAReaderPromptly(DecoderType type) throws Exception {
        Decoder decoder = decoders(type).open(server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(4000)));
        CountDownLatch firstBytes = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicLong finishedAt = new AtomicLong();
        Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[4096];
            try (InputStream pcm = decoder.pcm()) {
                while (pcm.read(buffer) >= 0) {
                    firstBytes.countDown();
                }
            } catch (IOException e) {
                // ending with an exception is as good as ending with -1
            } finally {
                finishedAt.set(System.nanoTime());
                done.countDown();
            }
        });
        assertThat(firstBytes.await(10, TimeUnit.SECONDS)).isTrue();

        long closedAt = System.nanoTime();
        decoder.close();

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(finishedAt.get() - closedAt)).isLessThan(1000);
    }

    private static Result decode(DecoderType type, URI url) throws Exception {
        try (Decoder decoder = decoders(type).open(url)) {
            byte[] pcm = decoder.pcm().readAllBytes();
            return new Result(PcmAnalysis.of(pcm, FORMAT.sampleRate()), decoder.failure());
        }
    }

    private static Decoder.Factory decoders(DecoderType type) {
        return switch (type) {
            case LAVAPLAYER -> LavaplayerDecoder.factory(HttpClient.newHttpClient(), FORMAT, watchdog);
            case FFMPEG -> {
                requireFfmpeg();
                yield FfmpegDecoder.factory("ffmpeg", null, FORMAT);
            }
        };
    }

    private static void requireFfmpeg() {
        if (ffmpegInstalled == null) {
            ffmpegInstalled = ffmpegRuns();
        }
        if (!ffmpegInstalled && Boolean.parseBoolean(System.getenv("JAZZCLUB_REQUIRE_FFMPEG"))) {
            throw new AssertionError("ffmpeg is not installed, but JAZZCLUB_REQUIRE_FFMPEG is set");
        }
        assumeTrue(ffmpegInstalled, "ffmpeg is not installed");
    }

    private static boolean ffmpegRuns() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
