package com.tedredington.jazzclub.config;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.app.KeyAction;
import com.tedredington.jazzclub.app.PlayerLoop;
import com.tedredington.jazzclub.app.Radio;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.credentials.CommandRunner;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.pandora.UserCredentials;
import com.tedredington.jazzclub.testsupport.FakeAudioPlayer;
import com.tedredington.jazzclub.testsupport.StubPandoraClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;

@Timeout(15)
class PlayerLifecycleTest {

    @TempDir
    Path directory;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableConfigurationProperties({JazzclubProperties.class, PandoraProperties.class})
    @ComponentScan({"com.tedredington.jazzclub.lastfm", "com.tedredington.jazzclub.eventcmd"})
    static class Fixture {
        @Bean FakeAudioPlayer audioPlayer() { return new FakeAudioPlayer(); }
        @Bean StubPandoraClient pandoraClient() {
            StubPandoraClient client = new StubPandoraClient();
            client.stations = List.of(EVANS);
            client.playlists.add(List.of(song("Nardis", "200")));
            return client;
        }
        @Bean InstantSource clock() { return InstantSource.system(); }
        @Bean CredentialsProvider credentialsProvider() { return () -> new UserCredentials("fixture", "fixture"); }
        @Bean CommandRunner commandRunner() { return command -> "unused"; }
        @Bean Observer observer() { return new Observer(); }
        @Bean KeyAction actions() {
            return new KeyAction() {
                public Set<ActionId> ids() { return Set.of(ActionId.values()); }
                public void execute(ActionId id, com.tedredington.jazzclub.app.ActionContext context) { }
            };
        }
    }

    static class Observer implements AutoCloseable {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch stations = new CountDownLatch(1);
        final AtomicReference<Thread> finishThread = new AtomicReference<>();
        boolean closed;
        @EventListener
        public void on(PlayerEvent event) {
            assertThat(closed).as("final events precede listener destruction").isFalse();
            if (event.type() == EventType.SONG_START) started.countDown();
            if (event.type() == EventType.USER_GET_STATIONS) stations.countDown();
            if (event.type() == EventType.SONG_FINISH) finishThread.set(Thread.currentThread());
        }
        public void close() { closed = true; }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(AppConfiguration.class, TerminalConfiguration.class, PlayerLifecycle.class, Fixture.class)
                .withPropertyValues("jazzclub.config-file=" + directory.resolve("config"),
                        "jazzclub.state-file=" + directory.resolve("state"));
    }

    @Test
    void contextShutdownDeliversTheLastScrobbleAndScriptBeforeTheirWorkersClose() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer lastfm = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        lastfm.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        lastfm.start();
        Files.writeString(directory.resolve("lastfm-session"), "user = fixture\nsession_key = fixture-key\n");
        Path script = directory.resolve("event.sh");
        Path scriptLog = directory.resolve("events");
        Files.writeString(script, "#!/bin/sh\necho \"$1\" >> '" + scriptLog + "'\ncat > /dev/null\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        try {
            runner().withPropertyValues("jazzclub.lastfm.user=fixture", "jazzclub.lastfm.api-key=KEY",
                    "jazzclub.lastfm.api-secret=SECRET", "jazzclub.lastfm.endpoint=http://localhost:"
                            + lastfm.getAddress().getPort() + "/", "jazzclub.event-command=" + script)
                    .run(context -> {
                        FakeAudioPlayer player = context.getBean(FakeAudioPlayer.class);
                        context.getBean(Radio.class).tune(EVANS);
                        player.elapsed(Duration.ofSeconds(120));
                        var http = context.getBean("lastFmHttpClient", java.net.http.HttpClient.class);
                        context.close();
                        assertThat(player.stops()).isPositive();
                        assertThat(requests).anySatisfy(request -> assertThat(request).contains("method=track.scrobble"));
                        assertThat(Files.readAllLines(scriptLog)).contains("songfinish");
                        assertThat(http.awaitTermination(Duration.ofSeconds(2))).isTrue();
                    });
        } finally {
            lastfm.stop(0);
        }
    }

    @Test
    void shutdownInterruptsTheLoopAndCleansUpOnItsOwnThreadBeforeDestruction() {
        runner().withPropertyValues("jazzclub.autostart-station=" + EVANS.token()).run(context -> {
            PlayerLifecycle lifecycle = context.getBean(PlayerLifecycle.class);
            Observer observer = context.getBean(Observer.class);
            FakeAudioPlayer player = context.getBean(FakeAudioPlayer.class);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            PlayerLoop loop = context.getBean(PlayerLoop.class);
            Thread owner = Thread.ofPlatform().start(() -> {
                try { lifecycle.run(loop::run); }
                catch (Throwable e) { failure.set(e); }
            });
            try {
                assertThat(observer.started.await(5, TimeUnit.SECONDS)).isTrue();
                context.close();
                assertThat(owner.join(Duration.ofSeconds(2))).isTrue();
                assertThat(failure.get()).isNull();
                assertThat(observer.finishThread.get()).isSameAs(owner);
                assertThat(observer.closed).isTrue();
                assertThat(player.stops()).isPositive();
                assertThat(Files.readString(directory.resolve("state"))).contains("autostart_station = 200");
            } finally {
                owner.interrupt();
                owner.join(Duration.ofSeconds(2));
            }
        });
    }

    @Test
    void shutdownAlsoUnblocksAnInitialStationPrompt() {
        runner().run(context -> {
            context.getBean(StubPandoraClient.class).stations = List.of(EVANS,
                    com.tedredington.jazzclub.testsupport.TestData.HARD_BOP);
            PlayerLifecycle lifecycle = context.getBean(PlayerLifecycle.class);
            Observer observer = context.getBean(Observer.class);
            PlayerLoop loop = context.getBean(PlayerLoop.class);
            Thread owner = Thread.ofPlatform().start(() -> lifecycle.run(loop::run));
            try {
                assertThat(observer.stations.await(5, TimeUnit.SECONDS)).isTrue();
                context.close();
                assertThat(owner.join(Duration.ofSeconds(2))).isTrue();
                assertThat(Files.exists(directory.resolve("state"))).isTrue();
                assertThat(observer.finishThread.get()).isNull();
            } finally {
                owner.interrupt();
                owner.join(Duration.ofSeconds(2));
            }
        });
    }
}
