package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.nio.file.Files;
import java.time.Duration;

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
 * What only the built-in decoder does. What every decoder must do is in
 * {@code DecoderConformanceTest}.
 */
@Timeout(30)
class LavaplayerDecoderTest {

    private static final PcmFormat FORMAT = PcmFormat.of(0);

    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private static AudioFixtureServer server;
    private final Decoder.Factory decoders = LavaplayerDecoder.factory(HttpClient.newHttpClient(), FORMAT, watchdog);

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

    @Test
    void needsOneRequestForAnIndexFirstFile() throws Exception {
        URI url = server.mount("he-noise.m4a");

        decode(decoders, url);

        assertThat(server.requestsFor(url)).hasSize(1);
    }

    /**
     * The first response breaks after 20000 bytes; how many of them reach the client before the
     * failure shows depends on the operating system, so the resume starts at most there.
     */
    @Test
    void resumesWithARangeRequestWhereTheConnectionDropped() throws Exception {
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.droppingOnceAfter(20_000));

        Result result = decode(decoders, url);

        assertThat(result.failure()).isNull();
        assertThat(server.requestsFor(url)).hasSize(2).last().extracting(AudioFixtureServer.Request::range)
                .asString().matches("bytes=\\d+-")
                .satisfies(range -> assertThat(Long.parseLong(range.substring(6, range.length() - 1)))
                        .isPositive().isLessThanOrEqualTo(20_000L));
    }

    @Test
    void decodesTheSameAudioThroughADropOnEveryResponse() throws Exception {
        DownloadPolicy smallSteps = new DownloadPolicy(Duration.ofSeconds(5), Duration.ofSeconds(5), 1024 * 1024,
                50, 3, 4096, Duration.ofMillis(10), Duration.ofMillis(40));
        Decoder.Factory decoders = LavaplayerDecoder.factory(HttpClient.newHttpClient(), FORMAT, smallSteps, watchdog);
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.droppingEvery(8192));

        byte[] clean = pcm(decoders, server.mount("he-noise.m4a"));
        byte[] dropped = pcm(decoders, url);

        assertThat(dropped).isEqualTo(clean);
        assertThat(server.requestsFor(url)).hasSizeGreaterThan(4);
    }

    @Test
    void closingEndsTheDownload() throws Exception {
        Decoder decoder = decoders.open(server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(4000)));
        decoder.pcm().readNBytes(1000);

        decoder.close();

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("song-download"))
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(t -> t.getName().equals("song-download"));
    }

    @Test
    void playsAnIndexAtTheEndEvenWithoutRangeSupport() throws Exception {
        Result result = decode(decoders, server.mount("he-noise-moov-last.m4a", Behaviour.NORMAL.withoutRanges()));

        assertThat(result.failure()).isNull();
        assertThat(result.pcm().seconds()).isGreaterThan(8.0);
    }

    @Test
    void closingIsNotAFailure() throws Exception {
        Decoder decoder = decoders.open(server.mount("he-noise.m4a", Behaviour.NORMAL.throttled(4000)));
        decoder.pcm().readNBytes(1000);

        decoder.close();

        assertThat(decoder.failure()).isNull();
    }

    @Test
    void refusesToResampleAndSaysWhatToDoInstead() throws Exception {
        Decoder.Factory at48k = LavaplayerDecoder.factory(HttpClient.newHttpClient(), PcmFormat.of(48_000), watchdog);

        Result result = decode(at48k, server.mount("lc-1k.m4a"));

        assertThat(result.failure()).contains("44100").contains("decoder = ffmpeg");
    }

    private static byte[] pcm(Decoder.Factory factory, URI url) throws Exception {
        try (Decoder decoder = factory.open(url)) {
            byte[] pcm = decoder.pcm().readAllBytes();
            assertThat(decoder.failure()).isNull();
            return pcm;
        }
    }

    private static Result decode(Decoder.Factory factory, URI url) throws Exception {
        try (Decoder decoder = factory.open(url)) {
            byte[] pcm = decoder.pcm().readAllBytes();
            return new Result(PcmAnalysis.of(pcm, FORMAT.sampleRate()), decoder.failure());
        }
    }
}
