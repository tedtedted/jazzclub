package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;

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

    private static AudioFixtureServer server;
    private final Decoder.Factory decoders = LavaplayerDecoder.factory(HttpClient.newHttpClient(), FORMAT);

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
    }

    @Test
    void needsOneRequestForAnIndexFirstFile() throws Exception {
        URI url = server.mount("he-noise.m4a");

        decode(decoders, url);

        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void resumesWithARangeRequestWhereTheConnectionDropped() throws Exception {
        URI url = server.mount("he-noise.m4a", Behaviour.NORMAL.droppingOnceAfter(20_000));

        decode(decoders, url);

        assertThat(server.requestsFor(url)).last().extracting(AudioFixtureServer.Request::range)
                .isEqualTo("bytes=20000-");
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
        Decoder.Factory at48k = LavaplayerDecoder.factory(HttpClient.newHttpClient(), PcmFormat.of(48_000));

        Result result = decode(at48k, server.mount("lc-1k.m4a"));

        assertThat(result.failure()).contains("44100").contains("decoder = ffmpeg");
    }

    private static Result decode(Decoder.Factory factory, URI url) throws Exception {
        try (Decoder decoder = factory.open(url)) {
            byte[] pcm = decoder.pcm().readAllBytes();
            return new Result(PcmAnalysis.of(pcm, FORMAT.sampleRate()), decoder.failure());
        }
    }
}
