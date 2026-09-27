package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.tedredington.jazzclub.testsupport.AudioFixtureServer;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Behaviour;

@Timeout(20)
class HttpSeekableStreamTest {

    private static final String FIXTURE = "he-noise.m4a";

    private static AudioFixtureServer server;
    private static byte[] file;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws IOException {
        server = new AudioFixtureServer();
        try (InputStream in = HttpSeekableStreamTest.class.getResourceAsStream("/fixtures/audio/" + FIXTURE)) {
            file = in.readAllBytes();
        }
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void readsTheWholeFileInOneRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            assertThat(stream.getContentLength()).isEqualTo(file.length);
            assertThat(stream.readAllBytes()).isEqualTo(file);
            assertThat(stream.getPosition()).isEqualTo(file.length);
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void seeksBackWithARangeRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            stream.skipFully(30_000);
            stream.seek(1000);

            assertThat(stream.getPosition()).isEqualTo(1000);
            assertThat(stream.readNBytes(100)).isEqualTo(Arrays.copyOfRange(file, 1000, 1100));
        }
        assertThat(server.requestsFor(url)).extracting(AudioFixtureServer.Request::range)
                .containsExactly(null, "bytes=1000-");
    }

    @Test
    void skipsShortDistancesWithoutANewRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            stream.seek(40_000);

            assertThat(stream.readNBytes(10)).isEqualTo(Arrays.copyOfRange(file, 40_000, 40_010));
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void seeksOnAServerWithoutRangesByStartingOver() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.withoutRanges());

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            stream.skipFully(30_000);
            stream.seek(1000);

            assertThat(stream.readNBytes(100)).isEqualTo(Arrays.copyOfRange(file, 1000, 1100));
        }
    }

    @Test
    void resumesWhereTheConnectionDropped() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.droppingOnceAfter(10_000));

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            assertThat(stream.readAllBytes()).isEqualTo(file);
        }
        assertThat(server.requestsFor(url)).extracting(AudioFixtureServer.Request::range)
                .containsExactly(null, "bytes=10000-");
    }

    @Test
    void failsWhenTheServerCannotResume() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.dyingAfter(10_000));

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url)) {
            assertThatThrownBy(stream::readAllBytes).isInstanceOf(IOException.class).hasMessageContaining("503");
        }
    }

    @Test
    void failsOnAnHttpError() {
        URI missing = server.mount(FIXTURE).resolve("/nothing-here.m4a");

        assertThatThrownBy(() -> HttpSeekableStream.open(http, missing)).hasMessage("HTTP 404");
    }

    @Test
    void refusesToReadAfterClose() throws Exception {
        HttpSeekableStream stream = HttpSeekableStream.open(http, server.mount(FIXTURE));
        stream.close();

        assertThatThrownBy(stream::read).hasMessage("Stream closed");
        assertThat(stream.canSeekHard()).isTrue();
        assertThat(stream.getTrackInfoProviders()).isEmpty();
    }
}
