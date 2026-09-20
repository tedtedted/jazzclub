package com.tedredington.jazzclub.remote;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.ui.Console;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pianobar's remote control: whatever is written to a named pipe is treated as if it had been typed.
 * {@code echo -n n > ~/.config/jazzclub/ctl} skips the song; prompts can be answered the same way.
 * The pipe is never created here; like pianobar, jazzclub only uses one the user made with {@code mkfifo}.
 */
public final class ControlFifo implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ControlFifo.class);
    private static final long WAKE_UP_TIMEOUT_MILLIS = 1_000;

    private final Path path;
    private final EventQueue events;
    private final Console console;
    private volatile RandomAccessFile pipe;
    private volatile boolean closing;
    private Thread reader;

    public ControlFifo(Path path, EventQueue events, Console console) {
        this.path = path;
        this.events = events;
        this.console = console;
    }

    /** @return whether the pipe is now being read */
    public synchronized boolean open() {
        if (pipe != null) {
            return true;
        }
        if (!Files.exists(path)) {
            return false; // not set up; perfectly normal
        }
        if (!isFifo(path)) {
            console.error("File at " + path + " is not a fifo\n");
            return false;
        }
        try {
            // Read-write, like pianobar: opening read-only would block until somebody writes, and
            // would hit end-of-file every time a writer closes.
            pipe = new RandomAccessFile(path.toFile(), "rw");
        } catch (IOException e) {
            console.error("Cannot open control fifo " + path + ": " + e.getMessage() + "\n");
            return false;
        }
        console.info("Control fifo at " + path + " opened\n");
        closing = false;
        reader = Thread.ofPlatform().name("control-fifo").daemon(true).start(this::readKeys);
        return true;
    }

    private void readKeys() {
        RandomAccessFile source = pipe;
        try (Reader reader = new InputStreamReader(new FileInputStream(source.getFD()), StandardCharsets.UTF_8)) {
            int c;
            while ((c = reader.read()) >= 0 && !closing) {
                events.publish(new Event.KeyPressed((char) c));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Control fifo reader ended", e);
        }
    }

    /**
     * Closing a pipe while another thread is blocked reading it hangs on macOS, which would hang
     * quitting. The pipe is open for writing too, so wake the reader with a byte of our own, let it
     * leave, and only then close. If the reader does not leave, the descriptor is left to process exit
     * rather than risking the hang.
     */
    @Override
    public synchronized void close() {
        RandomAccessFile source = pipe;
        pipe = null;
        if (source == null) {
            return;
        }
        closing = true;
        try {
            source.write('\n');
            reader.join(WAKE_UP_TIMEOUT_MILLIS);
            if (reader.isAlive()) {
                log.debug("Control fifo reader did not stop; leaving the pipe open");
                return;
            }
            source.close();
        } catch (IOException e) {
            log.debug("Closing the control fifo failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A named pipe is neither file, directory nor link; together with "exists" that is telling enough. */
    public static boolean isFifo(Path path) {
        try {
            Object mode = Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            return mode instanceof Integer m && (m & 0170000) == 0010000;
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            try {
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther();
            } catch (IOException unreadable) {
                return false;
            }
        }
    }
}
