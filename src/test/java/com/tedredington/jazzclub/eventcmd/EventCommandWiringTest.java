package com.tedredington.jazzclub.eventcmd;

import static com.tedredington.jazzclub.testsupport.TestData.EVANS;
import static com.tedredington.jazzclub.testsupport.TestData.song;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import com.tedredington.jazzclub.app.event.EventResult;
import com.tedredington.jazzclub.app.event.EventType;
import com.tedredington.jazzclub.app.event.PlayerEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole chain through Spring: event bus, listener bean, configured script. */
@SpringBootTest
@Timeout(30)
class EventCommandWiringTest {

    private static final Path DIRECTORY = createDirectory();
    private static final Path LOG = DIRECTORY.resolve("log");

    @Autowired
    private ApplicationEventPublisher publisher;

    @DynamicPropertySource
    static void eventCommand(DynamicPropertyRegistry registry) throws IOException {
        Path script = DIRECTORY.resolve("eventcmd.sh");
        Files.writeString(script, "#!/bin/sh\necho \"$1\" >> '" + LOG + "'\ngrep '^title=' >> '" + LOG + "'\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        registry.add("jazzclub.event-command", script::toString);
    }

    @Test
    void aPublishedPlayerEventReachesTheConfiguredScript() throws Exception {
        publisher.publishEvent(new PlayerEvent(EventType.SONG_START, EventResult.OK, EVANS, song("Nardis", "200"),
                null, Duration.ZERO, List.of(), List.of(EVANS)));

        for (int i = 0; i < 100 && (!Files.exists(LOG) || Files.readAllLines(LOG).size() < 2); i++) {
            Thread.sleep(50);
        }
        assertThat(Files.readAllLines(LOG)).containsExactly("songstart", "title=Nardis");
    }

    private static Path createDirectory() {
        try {
            return Files.createTempDirectory("jazzclub-eventcmd");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
