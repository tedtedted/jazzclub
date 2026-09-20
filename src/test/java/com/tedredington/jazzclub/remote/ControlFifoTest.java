package com.tedredington.jazzclub.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.testsupport.RecordingConsole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real named pipe: whether the operating system plays along is the whole point. */
@Timeout(20)
class ControlFifoTest {

    @TempDir
    Path directory;

    private final EventQueue events = new EventQueue();
    private final RecordingConsole console = new RecordingConsole();
    private ControlFifo fifo;

    @AfterEach
    void close() {
        if (fifo != null) {
            fifo.close();
        }
    }

    private Path mkfifo() throws IOException, InterruptedException {
        Path pipe = directory.resolve("ctl");
        Process process = new ProcessBuilder("mkfifo", pipe.toString()).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return pipe;
    }

    private List<Character> take(int count) throws InterruptedException {
        List<Character> keys = new ArrayList<>();
        while (keys.size() < count) {
            if (events.take() instanceof Event.KeyPressed(char key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    void whatIsWrittenToThePipeArrivesAsKeyPresses() throws Exception {
        Path pipe = mkfifo();
        fifo = new ControlFifo(pipe, events, console);

        assertThat(fifo.open()).isTrue();
        Files.writeString(pipe, "n+");

        assertThat(take(2)).containsExactly('n', '+');
        assertThat(console.output()).isEqualTo("(i) Control fifo at " + pipe + " opened\n");
    }

    @Test
    void thePipeSurvivesWritersComingAndGoing() throws Exception {
        Path pipe = mkfifo();
        fifo = new ControlFifo(pipe, events, console);
        fifo.open();

        Files.writeString(pipe, "p");
        assertThat(take(1)).containsExactly('p');
        // the first writer has closed; a read-only reader would have hit end-of-file here
        Files.writeString(pipe, "s12\n");

        assertThat(take(4)).containsExactly('s', '1', '2', '\n');
    }

    @Test
    void noPipeIsNormalAndSilent() {
        fifo = new ControlFifo(directory.resolve("absent"), events, console);

        assertThat(fifo.open()).isFalse();
        assertThat(console.output()).isEmpty();
    }

    @Test
    void anOrdinaryFileIsRefusedSoItIsNeverReadAsCommands() throws IOException {
        Path file = Files.writeString(directory.resolve("ctl"), "q");
        fifo = new ControlFifo(file, events, console);

        assertThat(fifo.open()).isFalse();
        assertThat(console.output()).isEqualTo("/!\\ File at " + file + " is not a fifo\n");
    }

    @Test
    void aDirectoryIsRefusedToo() {
        fifo = new ControlFifo(directory, events, console);

        assertThat(fifo.open()).isFalse();
        assertThat(ControlFifo.isFifo(directory)).isFalse();
        assertThat(ControlFifo.isFifo(directory.resolve("absent"))).isFalse();
    }

    /** Regression: closing while the reader waits for input used to hang on macOS, and with it quitting. */
    @Test
    @Timeout(5)
    void closingAnIdlePipeReturnsPromptlyAndPublishesNothing() throws Exception {
        Path pipe = mkfifo();
        fifo = new ControlFifo(pipe, events, console);
        fifo.open();
        Thread.sleep(200); // let the reader block in read()

        long start = System.nanoTime();
        fifo.close();

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_500);
        events.publish(new Event.Tick());
        assertThat(events.take()).as("the wake-up byte is not a key press").isEqualTo(new Event.Tick());
    }

    @Test
    void openingTwiceAndClosingTwiceAreHarmless() throws Exception {
        Path pipe = mkfifo();
        fifo = new ControlFifo(pipe, events, console);

        assertThat(fifo.open()).isTrue();
        assertThat(fifo.open()).isTrue();
        fifo.close();
        fifo.close();

        assertThat(console.output()).containsOnlyOnce("opened");
    }
}
