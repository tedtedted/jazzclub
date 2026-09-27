package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.tedredington.jazzclub.player.Decoder;
import com.tedredington.jazzclub.player.PcmFormat;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Behaviour;
import com.tedredington.jazzclub.testsupport.PcmAnalysis;

/**
 * The in-process decoder against the synthetic fixtures (see issue #9 for how each was chosen).
 * Tones are -12 dBFS; the noise is white, so every band should match the 1-4 kHz band.
 */
@Timeout(30)
class LavaplayerDecoderTest {

    private static final double TONE_DB = 20 * Math.log10(0.25);
    /**
     * Seconds a decode may run over the source: LavaPlayer's MP4 parser ignores the edit list, so
     * the encoder's priming (2112 samples) and the SBR delay stay in as ~60 ms of leading silence.
     * ffmpeg trims them. Inaudible at the start of a radio song.
     */
    private static final double UNTRIMMED_PRIMING = 0.25;
    private static final PcmFormat FORMAT = PcmFormat.of(0);

    private static AudioFixtureServer server;
    private static Path natives;
    private final Decoder.Factory decoders = LavaplayerDecoder.factory(HttpClient.newHttpClient(), FORMAT);

    private record Result(PcmAnalysis pcm, String failure) {
    }

    @BeforeAll
    static void start() throws IOException {
        server = new AudioFixtureServer();
        natives = Files.createTempDirectory("jazzclub-natives");
        LavaplayerNatives.prepare(natives);
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void decodesAacLowComplexity() throws Exception {
        Result result = decode(server.mount("lc-1k.m4a"));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().toneDb(0, 1000)).isCloseTo(TONE_DB, within(1.0));
        assertThat(result.pcm().seconds()).isBetween(3.0, 3.0 + UNTRIMMED_PRIMING);
    }

    @Test
    void decodesTheSbrBandOfHeAac() throws Exception {
        Result result = decode(server.mount("he-noise.m4a"));

        assertThat(result.failure()).isNull();
        // without SBR there is nothing above ~11 kHz; with it, white noise stays flat
        assertThat(result.pcm().bandDb(12000, 15000) - result.pcm().bandDb(1000, 4000)).isGreaterThan(-6);
        assertThat(result.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
    }

    @Test
    void keepsParametricStereoApart() throws Exception {
        Result result = decode(server.mount("hev2-panned.m4a"));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().toneDb(0, 3000) - result.pcm().toneDb(0, 5000)).isGreaterThan(10);
        assertThat(result.pcm().toneDb(1, 5000) - result.pcm().toneDb(1, 3000)).isGreaterThan(10);
    }

    @Test
    void playsAnIndexAtTheEndWithOrWithoutRangeSupport() throws Exception {
        Result ranges = decode(server.mount("he-noise-moov-last.m4a"));
        Result noRanges = decode(server.mount("he-noise-moov-last.m4a", Behaviour.NORMAL.withoutRanges()));

        assertThat(ranges.failure()).isNull();
        assertThat(ranges.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
        assertThat(noRanges.failure()).isNull();
        assertThat(noRanges.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
    }

    @Test
    void needsOneRequestForAnIndexFirstFile() throws Exception {
        URI url = server.mount("he-noise.m4a");

        decode(url);

        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void resumesAfterTheConnectionDrops() throws Exception {
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.droppingOnceAfter(20_000));

        Result result = decode(url);

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().seconds()).isBetween(8.0, 8.0 + UNTRIMMED_PRIMING);
        assertThat(server.requestsFor(url)).last().extracting(AudioFixtureServer.Request::range)
                .isEqualTo("bytes=20000-");
    }

    @Test
    void reportsAServerThatDiesInsteadOfEndingTheSongEarly() throws Exception {
        Result result = decode(server.mount("he-noise.m4a", Behaviour.NORMAL.dyingAfter(20_000)));

        assertThat(result.failure()).contains("503");
    }

    @Test
    void reportsATruncatedFile() throws Exception {
        assertThat(decode(server.mount("truncated.m4a")).failure()).isNotNull();
    }

    @Test
    void reportsFilesThatAreNotMp4() throws Exception {
        assertThat(decode(server.mount("garbage.m4a")).failure()).isNotNull();
        assertThat(decode(server.mount("empty.m4a")).failure()).isNotNull();
    }

    @Test
    void reportsHttpErrors() throws Exception {
        URI missing = server.mount("lc-1k.m4a").resolve("/no-such-file.m4a");

        assertThat(decode(missing).failure()).contains("HTTP 404");
    }

    @Test
    void refusesAnythingButHttp() {
        assertThatThrownBy(() -> decoders.open(URI.create("file:///etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closeUnblocksAReaderPromptly() throws Exception {
        Decoder decoder = decoders.open(server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(4000)));
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
        assertThat(decoder.failure()).isNull();
    }

    @Test
    void refusesToResampleAndSaysWhatToDoInstead() throws Exception {
        Decoder.Factory at48k = LavaplayerDecoder.factory(HttpClient.newHttpClient(), PcmFormat.of(48_000));

        Result result = decode(at48k, server.mount("lc-1k.m4a"));

        assertThat(result.failure()).contains("44100").contains("decoder = ffmpeg");
    }

    private Result decode(URI url) throws Exception {
        return decode(decoders, url);
    }

    private static Result decode(Decoder.Factory factory, URI url) throws Exception {
        try (Decoder decoder = factory.open(url)) {
            byte[] pcm = decoder.pcm().readAllBytes();
            return new Result(PcmAnalysis.of(pcm, FORMAT.sampleRate()), decoder.failure());
        }
    }
}
