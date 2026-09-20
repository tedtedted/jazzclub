package com.tedredington.jazzclub.eventcmd;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.app.event.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the user's {@code event_command} for every event: the event name as its only argument, the
 * details on standard input.
 *
 * <p>Unlike pianobar, which waits for the script, scripts run on a thread of their own, one at a time
 * and in order. A scrobbler talking to a slow server therefore never delays the music or a key press.
 * A script that hangs is killed after a timeout so it cannot hold up the ones behind it.
 */
public final class EventCommandRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventCommandRunner.class);

    private final String command;
    private final Duration timeout;
    private final Duration drainOnClose;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("event-command").daemon(true).unstarted(runnable));

    /**
     * @param command      path of the executable; started directly, not through a shell, like pianobar
     * @param drainOnClose how long {@link #close()} lets pending scripts finish, so the final
     *                     {@code songfinish} still reaches a scrobbler when the user quits
     */
    public EventCommandRunner(String command, Duration timeout, Duration drainOnClose) {
        this.command = command;
        this.timeout = timeout;
        this.drainOnClose = drainOnClose;
    }

    public void submit(PlayerEvent event) {
        String name = event.type().pianobarName();
        String input = EventCommandFormatter.format(event);
        executor.execute(() -> run(name, input));
    }

    private void run(String eventName, String input) {
        Process process;
        try {
            process = new ProcessBuilder(command, eventName)
                    // the terminal belongs to the player; a chatty script must not scribble over it
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            log.warn("Cannot start event_command '{}': {}", command, e.getMessage());
            return;
        }
        try {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // a script that does not read its input closes the pipe early; that is its business
                log.debug("event_command did not read all of its input for {}", eventName);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("event_command still running after {}s for '{}', killing it", timeout.toSeconds(), eventName);
            } else if (process.exitValue() != 0) {
                log.debug("event_command exited with status {} for {}", process.exitValue(), eventName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(drainOnClose.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
