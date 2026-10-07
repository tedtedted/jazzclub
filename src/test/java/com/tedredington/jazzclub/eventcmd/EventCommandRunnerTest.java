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
import org.junit.jupiter.api.io.TempDir;

/** Runs real shell scripts, because the contract is with the operating system: argv, stdin, exit. */
@Timeout(20)
class EventCommandRunnerTest {

    @TempDir
    Path directory;

    private Path script(String body) throws IOException {
        Path script = directory.resolve("eventcmd.sh");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static PlayerEvent event(EventType type, String title) {
        return new PlayerEvent(type, EventResult.OK, EVANS, song(title, "200"), null, Duration.ofSeconds(7),
                List.of(), List.of(EVANS));
    }

    @Test
    void theScriptGetsTheEventNameAsArgumentAndTheDetailsOnStdin() throws IOException {
        Path log = directory.resolve("log");
        Path script = script("echo \"event=$1 args=$#\" >> '" + log + "'; cat >> '" + log + "'");

        try (EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofSeconds(10),
                Duration.ofSeconds(10))) {
            runner.submit(event(EventType.SONG_START, "Peace Piece"));
        }

        assertThat(Files.readString(log))
                .startsWith("event=songstart args=1\nstationName=Bill Evans Radio\n")
                .contains("title=Peace Piece\n")
                .contains("songPlayed=7\n")
                .endsWith("station0=Bill Evans Radio\n");
    }

    @Test
    void eventsAreDeliveredOneAtATimeAndInOrder() throws IOException {
        Path log = directory.resolve("log");
        // the first script is slow; were scripts run in parallel the second would overtake it
        Path script = script("grep '^title=' > '" + directory + "/in'; "
                + "case \"$1\" in songstart) sleep 0.5;; esac; "
                + "echo \"$1 $(cat '" + directory + "/in')\" >> '" + log + "'");

        try (EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofSeconds(10),
                Duration.ofSeconds(10))) {
            runner.submit(event(EventType.SONG_START, "one"));
            runner.submit(event(EventType.SONG_FINISH, "one"));
            runner.submit(event(EventType.SONG_START, "two"));
        }

        assertThat(Files.readAllLines(log)).containsExactly(
                "songstart title=one", "songfinish title=one", "songstart title=two");
    }

    @Test
    void submittingNeverBlocksThePlayer() throws IOException {
        Path script = script("sleep 2");

        EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofSeconds(10), Duration.ZERO);
        long start = System.nanoTime();
        runner.submit(event(EventType.SONG_START, "x"));
        long submitMillis = (System.nanoTime() - start) / 1_000_000;
        runner.close();

        assertThat(submitMillis).isLessThan(500);
    }

    @Test
    void aHangingScriptIsKilledSoTheNextEventStillGoesOut() throws IOException {
        Path log = directory.resolve("log");
        Path script = script("case \"$1\" in songstart) sleep 96 | cat;; *) echo \"$1\" >> '" + log + "';; esac");

        try (EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofMillis(400),
                Duration.ofSeconds(10))) {
            runner.submit(event(EventType.SONG_START, "x"));
            runner.submit(event(EventType.SONG_FINISH, "x"));
        }

        assertThat(Files.readAllLines(log)).containsExactly("songfinish");
        assertThat(ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c -> c.endsWith("/sleep")).orElse(false))
                .filter(p -> p.info().arguments().map(a -> List.of(a).contains("96")).orElse(false))
                .count()).as("orphaned sleeps").isZero();
    }

    @Test
    void timeoutAlsoCoversAFullInputPipe() throws IOException {
        Path log = directory.resolve("log");
        Path script = script("case \"$1\" in songstart) sleep 96 | cat;; *) cat > /dev/null; echo \"$1\" >> '"
                + log + "';; esac");

        try (EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofMillis(400),
                Duration.ofSeconds(10))) {
            runner.submit(event(EventType.SONG_START, "x".repeat(2_000_000)));
            runner.submit(event(EventType.SONG_FINISH, "x"));
        }

        assertThat(Files.readAllLines(log)).containsExactly("songfinish");
    }

    @Test
    void aScriptThatIgnoresItsInputOrFailsDoesNoHarm() throws IOException {
        Path script = script("exit 3");

        try (EventCommandRunner runner = new EventCommandRunner(script.toString(), Duration.ofSeconds(10),
                Duration.ofSeconds(10))) {
            runner.submit(event(EventType.SONG_START, "x"));
            runner.submit(event(EventType.SONG_FINISH, "x"));
        }
        // reaching this line without an exception is the assertion
    }

    @Test
    void aMissingScriptIsLoggedNotThrown() {
        try (EventCommandRunner runner = new EventCommandRunner(directory.resolve("nope").toString(),
                Duration.ofSeconds(1), Duration.ofSeconds(5))) {
            runner.submit(event(EventType.SONG_START, "x"));
        }
    }

    @Test
    void homeDirectoryShorthandIsExpandedBecauseNoShellIsInvolved() {
        assertThat(EventCommandListener.expandHome("~/.config/jazzclub/eventcmd"))
                .isEqualTo(System.getProperty("user.home") + "/.config/jazzclub/eventcmd");
        assertThat(EventCommandListener.expandHome("/usr/bin/notify")).isEqualTo("/usr/bin/notify");
        assertThat(EventCommandListener.expandHome("~other/x")).isEqualTo("~other/x");
    }
}
