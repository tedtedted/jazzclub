package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.tedredington.jazzclub.testsupport.AudioFixtureServer;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Behaviour;

@Timeout(20)
class HttpSeekableStreamTest {

    private static final String FIXTURE = "he-noise.m4a";
    private static final DownloadPolicy POLICY = new DownloadPolicy(Duration.ofMillis(500), Duration.ofMillis(300),
            1024 * 1024, 50, 3, 4096, Duration.ofMillis(10), Duration.ofMillis(40));

    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
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
        watchdog.shutdownNow();
    }

    @Test
    void readsTheWholeFileInOneRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            assertThat(stream.getContentLength()).isEqualTo(file.length);
            assertThat(stream.readAllBytes()).isEqualTo(file);
            assertThat(stream.getPosition()).isEqualTo(file.length);
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void seeksBackWithoutANewRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            stream.skipFully(30_000);
            stream.seek(1000);

            assertThat(stream.getPosition()).isEqualTo(1000);
            assertThat(stream.readNBytes(100)).isEqualTo(Arrays.copyOfRange(file, 1000, 1100));
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void seeksForwardPastWhatHasArrivedAndWaitsForIt() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.throttled(40_000));

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            stream.seek(file.length - 100);

            assertThat(stream.readNBytes(100)).isEqualTo(Arrays.copyOfRange(file, file.length - 100, file.length));
            assertThat(stream.read()).isEqualTo(-1);
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void closingWakesAReadWaitingForData() throws Exception {
        HttpSeekableStream stream = HttpSeekableStream.open(http, server.mount(FIXTURE,
                Behaviour.NORMAL.throttled(2000)), POLICY, watchdog);
        stream.seek(file.length - 1);
        CompletableFuture<Integer> reader = CompletableFuture.supplyAsync(() -> {
            try {
                return stream.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(100);

        stream.close();

        assertThatThrownBy(() -> reader.get(1, TimeUnit.SECONDS)).hasMessageContaining("Stream closed");
        assertThat(stream.download().awaitWorkerExit(Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void skipsShortDistancesWithoutANewRequest() throws Exception {
        URI url = server.mount(FIXTURE);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            stream.seek(40_000);

            assertThat(stream.readNBytes(10)).isEqualTo(Arrays.copyOfRange(file, 40_000, 40_010));
        }
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void seeksOnAServerWithoutRangesByStartingOver() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.withoutRanges());

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            stream.skipFully(30_000);
            stream.seek(1000);

            assertThat(stream.readNBytes(100)).isEqualTo(Arrays.copyOfRange(file, 1000, 1100));
        }
    }

    @Test
    void resumesWhereTheConnectionDropped() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.droppingOnceAfter(10_000));

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            assertThat(stream.readAllBytes()).isEqualTo(file);
        }
        assertResumedOnceWithinTheFirst(url, 10_000);
    }

    @Test
    void failsWhenTheServerCannotResume() throws Exception {
        URI url = server.mount(FIXTURE, Behaviour.NORMAL.dyingAfter(10_000));

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, url, POLICY, watchdog)) {
            assertThatThrownBy(stream::readAllBytes).isInstanceOf(IOException.class).hasMessageContaining("503");
        }
    }

    @Test
    void followsARedirectAndResumesAtTheNewLocation() throws Exception {
        URI real = server.mount(FIXTURE, Behaviour.NORMAL.droppingOnceAfter(10_000));
        URI redirect = server.redirectTo(real);

        try (HttpSeekableStream stream = HttpSeekableStream.open(http, redirect, POLICY, watchdog)) {
            assertThat(stream.readAllBytes()).isEqualTo(file);
        }
        assertThat(server.requestsFor(redirect)).hasSize(1);
        assertResumedOnceWithinTheFirst(real, 10_000);
    }

    @Test
    void givesUpAfterTooManyRedirects() {
        URI url = server.mount(FIXTURE);
        for (int i = 0; i < 7; i++) {
            url = server.redirectTo(url);
        }
        URI start = url;

        assertThatThrownBy(() -> HttpSeekableStream.open(http, start, POLICY, watchdog)).hasMessageContaining("too many redirects");
    }

    @Test
    void failsOnAnHttpError() {
        URI missing = server.mount(FIXTURE).resolve("/nothing-here.m4a");

        assertThatThrownBy(() -> HttpSeekableStream.open(http, missing, POLICY, watchdog)).hasMessage("HTTP 404");
    }

    @Test
    void refusesToReadAfterClose() throws Exception {
        HttpSeekableStream stream = HttpSeekableStream.open(http, server.mount(FIXTURE), POLICY, watchdog);
        stream.close();

        assertThatThrownBy(stream::read).hasMessage("Stream closed");
        assertThat(stream.canSeekHard()).isTrue();
        assertThat(stream.getTrackInfoProviders()).isEmpty();
    }

    /**
     * The first response broke after {@code sent} bytes, so the resume starts at most there: how
     * much of it reached the client before the connection failed depends on the operating system.
     * That the whole file arrived is what counts, and is asserted by the caller.
     */
    private static void assertResumedOnceWithinTheFirst(URI url, int sent) {
        assertThat(server.requestsFor(url)).extracting(AudioFixtureServer.Request::range)
                .hasSize(2)
                .satisfies(ranges -> assertThat(ranges.get(0)).isNull())
                .satisfies(ranges -> assertThat(ranges.get(1)).matches("bytes=\\d+-")
                        .satisfies(range -> assertThat(Long.parseLong(range.substring(6, range.length() - 1)))
                                .isPositive().isLessThanOrEqualTo(sent)));
    }
}
