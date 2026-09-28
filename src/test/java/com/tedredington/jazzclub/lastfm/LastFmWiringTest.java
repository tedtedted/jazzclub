package com.tedredington.jazzclub.lastfm;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.ui.MessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole chain: config file, Spring, event bus, listener, HTTP, and the notice for the main loop,
 * against a local stand-in for Last.fm.
 */
// the config file must be known before @DynamicPropertySource applies, so its path is fixed
@SpringBootTest(properties = {"jazzclub.config-file=" + LastFmWiringTest.CONFIG,
        "jazzclub.state-file=" + LastFmWiringTest.DIRECTORY + "/state"})
@Timeout(30)
class LastFmWiringTest {

    static final String DIRECTORY = "target/lastfm-wiring-test";
    static final String CONFIG = DIRECTORY + "/config";

    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer LAST_FM = startServer();

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private EventQueue events;

    @DynamicPropertySource
    static void lastFm(DynamicPropertyRegistry registry) {
        registry.add("jazzclub.lastfm.api-key", () -> "KEY");
        registry.add("jazzclub.lastfm.api-secret", () -> "SECRET");
        registry.add("jazzclub.lastfm.endpoint",
                () -> "http://localhost:" + LAST_FM.getAddress().getPort() + "/2.0/");
    }

    @AfterAll
    static void stopServer() {
        LAST_FM.stop(0);
    }

    @Test
    void theConfigFileIsAllItTakesToScrobble() throws Exception {
        publisher.publishEvent(new PlayerEvent(EventType.SONG_START, EventResult.OK, EVANS, song("Nardis", "200"),
                null, Duration.ZERO, List.of(), List.of(EVANS)));

        assertThat(events.take()).isEqualTo(new Event.Notice(MessageType.INFO, "Last.fm: scrobbling as Ted."));
        assertThat(REQUESTS).hasSize(2);
        assertThat(REQUESTS.get(0)).contains("method=auth.getMobileSession", "username=ted", "password=pw");
        assertThat(REQUESTS.get(1)).contains("method=track.updateNowPlaying", "sk=sk-1", "track=Nardis");
        assertThat(Files.readString(Path.of(DIRECTORY, "lastfm-session"))).contains("session_key = sk-1");
    }

    private static HttpServer startServer() {
        try {
            // written before the Spring context, which this class's static initialisation precedes
            Path directory = Path.of(DIRECTORY);
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve("lastfm-session"));
            Files.writeString(Path.of(CONFIG), "lastfm_user = ted\nlastfm_password = pw\n");
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/2.0/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(body);
                byte[] answer = (body.contains("auth.getMobileSession")
                        ? "{\"session\":{\"name\":\"Ted\",\"key\":\"sk-1\"}}"
                        : "{\"nowplaying\":{}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, answer.length);
                exchange.getResponseBody().write(answer);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
