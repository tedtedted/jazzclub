package com.tedredington.jazzclub.player.lavaplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.tedredington.jazzclub.testsupport.AudioFixtureServer;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Behaviour;
import com.tedredington.jazzclub.testsupport.AudioFixtureServer.Request;

@Timeout(20)
class SongDownloadTest {

    /** Small and quick, so that each bound is reached in milliseconds. */
    private static final DownloadPolicy POLICY = new DownloadPolicy(Duration.ofMillis(500), Duration.ofMillis(300),
            1024 * 1024, 50, 3, 4096, Duration.ofMillis(10), Duration.ofMillis(40));
    /** The size of the file Codex's review probed the old stream with. */
    private static final byte[] FILE = new byte[98_304];
    private static final Duration EXIT = Duration.ofSeconds(2);

    private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private static AudioFixtureServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<SongDownload> downloads = new ArrayList<>();

    @BeforeAll
    static void start() throws IOException {
        new Random(42).nextBytes(FILE);
        server = new AudioFixtureServer();
    }

    @AfterAll
    static void stop() {
        server.close();
        watchdog.shutdownNow();
    }

    @AfterEach
    void closeAll() {
        downloads.forEach(SongDownload::close);
    }

    @Test
    void downloadsTheWholeFileWithoutWaitingForAReader() throws Exception {
        URI url = mount(Behaviour.NORMAL);

        SongDownload download = start(url, POLICY);

        // nothing reads, as during a long pause: the file still arrives
        assertThat(download.awaitWorkerExit(EXIT)).isTrue();
        assertThat(download.isComplete()).isTrue();
        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(server.requestsFor(url)).hasSize(1);
    }

    @Test
    void resumesAsOftenAsTheConnectionBreaksWhileEachTimeMakesProgress() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingEvery(8192));

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        // the old stream gave up after the fourth request
        assertThat(server.requestsFor(url)).hasSizeGreaterThanOrEqualTo(12)
                .allSatisfy(request -> assertThat(request.status()).isIn(200, 206));
    }

    @Test
    void givesUpOnAServerThatTricklesAFewBytesPerConnection() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingEvery(1000));

        SongDownload download = start(url, POLICY);

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("kept failing");
        assertThat(server.requestsFor(url)).hasSize(POLICY.maxAttemptsWithoutProgress());
    }

    @Test
    void givesUpOnAServerThatNeverRecoversWithoutARequestStorm() throws Exception {
        URI url = mount(Behaviour.NORMAL.dyingAfter(8192));

        SongDownload download = start(url, POLICY);

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("HTTP 503");
        assertThat(server.requestsFor(url)).hasSize(1 + POLICY.maxAttemptsWithoutProgress());
    }

    @Test
    void retriesTransientServerErrors() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).failingAfterDrop(503, 2));

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 503, 503, 206);
    }

    @Test
    void failsAtOnceWhenTheFileIsGone() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).failingAfterDrop(404, 1));

        SongDownload download = start(url, POLICY);

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("HTTP 404");
        assertThat(server.requestsFor(url)).hasSize(2);
    }

    @Test
    void refusesAResumeThatStartsSomewhereElse() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).misplacingRanges());

        SongDownload download = start(url, POLICY);

        // the old stream spliced the start of the file in again
        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("got the range bytes 0-");
        assertThat(download.downloaded()).isLessThanOrEqualTo(8192);
    }

    @Test
    void restartsFromTheBeginningOnAServerWithoutRanges() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).withoutRanges());

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 200);
    }

    @Test
    void noticesTheFileChangedWhenTheServerIgnoresTheRange() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).withoutRanges().changingAfterDrop());

        SongDownload download = start(url, POLICY);

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("changed");
    }

    @Test
    void noticesTheFileChangedThroughItsEtag() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).withEtag().changingAfterDrop());

        SongDownload download = start(url, POLICY);

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("changed");
        // If-Range made the server answer with the whole new file instead of a piece of it
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 200);
    }

    @Test
    void resumesWithIfRangeWhileTheFileIsUnchanged() throws Exception {
        URI url = mount(Behaviour.NORMAL.droppingOnceAfter(8192).withEtag());

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 206);
    }

    @Test
    void retriesWhenTheHeadersNeverArrive() throws Exception {
        URI url = mount(Behaviour.NORMAL.stallingHeadersOnce());
        long started = System.nanoTime();

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(elapsed(started)).isLessThan(Duration.ofSeconds(3));
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(0, 200);
    }

    @Test
    void reconnectsWhenTheBodyGoesSilent() throws Exception {
        URI url = mount(Behaviour.NORMAL.stallingBodyOnceAfter(8192));
        long started = System.nanoTime();

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(elapsed(started)).isLessThan(Duration.ofSeconds(3));
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 206);
    }

    @Test
    void downloadsAFileWithoutALength() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.withoutLength()), POLICY);

        assertThat(download.awaitLength()).isEqualTo(SongDownload.UNKNOWN_LENGTH);
        assertThat(readAll(download)).isEqualTo(FILE);
    }

    @Test
    void resumesAFileWithoutALength() throws Exception {
        URI url = mount(Behaviour.NORMAL.withoutLength().droppingOnceAfter(20_000));

        SongDownload download = start(url, POLICY);

        assertThat(readAll(download)).isEqualTo(FILE);
        assertThat(server.requestsFor(url)).extracting(Request::status).containsExactly(200, 206);
    }

    @Test
    void refusesAFileOverTheLimitBeforeKeepingAnyOfIt() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL), withMaxBytes(64 * 1024));

        assertThatThrownBy(download::awaitLength).hasMessageContaining("too large");
        assertThat(download.downloaded()).isZero();
        assertThat(download.retainedBytes()).isZero();
    }

    @Test
    void stopsAFileWithoutALengthAtTheLimit() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.withoutLength()), withMaxBytes(64 * 1024));

        assertThatThrownBy(() -> readAll(download)).hasMessageContaining("too large");
        assertThat(download.downloaded()).isLessThanOrEqualTo(64 * 1024);
    }

    @Test
    void readingAheadOfTheDownloadWaitsForTheBytes() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.throttled(100_000)), POLICY);
        byte[] last = new byte[4];

        int n = download.read(FILE.length - 4, last, 0, 4);

        assertThat(Arrays.copyOf(last, n)).isEqualTo(Arrays.copyOfRange(FILE, FILE.length - 4, FILE.length - 4 + n));
    }

    @Test
    void closingWhileWaitingForHeadersEndsTheDownloadAtOnce() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.stallingHeadersOnce()), slowPolicy());
        CompletableFuture<Long> waiting = CompletableFuture.supplyAsync(() -> {
            try {
                return download.awaitLength();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(100);

        download.close();

        assertThat(download.awaitWorkerExit(EXIT)).isTrue();
        assertThatThrownBy(() -> waiting.get(1, TimeUnit.SECONDS)).hasMessageContaining("closed");
    }

    @Test
    void closingDuringTheBodyEndsTheDownloadAndLetsGoOfTheBuffer() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.throttled(20_000)), POLICY);
        awaitBytes(download, 1);

        download.close();

        assertThat(download.awaitWorkerExit(EXIT)).isTrue();
        assertThat(download.retainedBytes()).isZero();
        assertThat(download.downloaded()).isLessThan(FILE.length);
    }

    @Test
    void closingDuringABackoffEndsTheDownloadAtOnce() throws Exception {
        DownloadPolicy longWaits = new DownloadPolicy(Duration.ofMillis(500), Duration.ofMillis(300), 1024 * 1024,
                50, 3, 4096, Duration.ofSeconds(30), Duration.ofSeconds(30));
        URI url = mount(Behaviour.NORMAL.dyingAfter(1000));
        SongDownload download = start(url, longWaits);
        while (server.requestsFor(url).isEmpty()) {
            Thread.sleep(10);
        }
        Thread.sleep(100);

        download.close();

        assertThat(download.awaitWorkerExit(EXIT)).isTrue();
    }

    @Test
    void closingWakesAReaderWaitingForBytes() throws Exception {
        SongDownload download = start(mount(Behaviour.NORMAL.throttled(4000)), POLICY);
        CompletableFuture<Integer> reader = CompletableFuture.supplyAsync(() -> {
            try {
                return download.read(FILE.length - 1, new byte[1], 0, 1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(100);

        download.close();

        assertThatThrownBy(() -> reader.get(1, TimeUnit.SECONDS)).hasMessageContaining("Stream closed");
    }

    @Test
    void skippingSongsQuicklyLeavesNoDownloadsBehind() throws Exception {
        List<SongDownload> skipped = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            SongDownload download = start(mount(Behaviour.NORMAL.throttled(20_000)), POLICY);
            if (i % 2 == 0) {
                awaitBytes(download, 1);
            }
            download.close();
            skipped.add(download);
        }

        for (SongDownload download : skipped) {
            assertThat(download.awaitWorkerExit(EXIT)).isTrue();
            assertThat(download.retainedBytes()).isZero();
        }
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(t -> t.getName().equals("song-download"));
    }

    private URI mount(Behaviour behaviour) {
        return server.mount("song.m4a", FILE, behaviour);
    }

    private SongDownload start(URI url, DownloadPolicy policy) {
        SongDownload download = new SongDownload(http, url, policy, watchdog);
        downloads.add(download);
        download.start();
        return download;
    }

    private static DownloadPolicy withMaxBytes(long maxBytes) {
        return new DownloadPolicy(POLICY.headersTimeout(), POLICY.stallTimeout(), maxBytes, POLICY.maxReconnects(),
                POLICY.maxAttemptsWithoutProgress(), POLICY.minProgressBytes(), POLICY.firstBackoff(),
                POLICY.maxBackoff());
    }

    /** Waits for headers far longer than the test, so that only close() can end the wait. */
    private static DownloadPolicy slowPolicy() {
        return new DownloadPolicy(Duration.ofSeconds(30), Duration.ofSeconds(30), 1024 * 1024, 50, 3, 4096,
                Duration.ofMillis(10), Duration.ofMillis(40));
    }

    private static byte[] readAll(SongDownload download) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buffer = new byte[5000];
        int n;
        while ((n = download.read(all.size(), buffer, 0, buffer.length)) >= 0) {
            all.write(buffer, 0, n);
        }
        return all.toByteArray();
    }

    private static void awaitBytes(SongDownload download, int bytes) throws InterruptedException {
        while (download.downloaded() < bytes) {
            Thread.sleep(5);
        }
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
