package com.tedredington.jazzclub.terminal;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the tty: switches it to raw mode so single key presses arrive without Enter, feeds them into
 * the event queue, and puts everything back on {@link #close()}, however the program ends.
 */
public final class TerminalSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TerminalSession.class);

    /**
     * Never read without a timeout. JLine's untimed read blocks on stdin while holding the lock
     * that {@link Terminal#close()} needs, so quitting would hang until the next key press.
     */
    private static final long READ_TIMEOUT_MILLIS = 200;

    private final EventQueue events;
    private volatile boolean closing;
    private Terminal terminal;
    private Attributes originalAttributes;

    public TerminalSession(EventQueue events) {
        this.events = events;
    }

    public synchronized void open() {
        if (terminal != null) {
            return;
        }
        try {
            terminal = TerminalBuilder.builder().system(true).graphemeCluster(false).build();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open the terminal", e);
        }
        originalAttributes = terminal.enterRawMode();
        // JLine turns the ^C byte into this signal instead of letting the OS deliver SIGINT.
        // Route it through the normal quit path so the player stops and the tty is restored.
        terminal.handle(Terminal.Signal.INT, signal -> events.publish(new Event.InputClosed()));
        log.debug("Terminal {} ({})", terminal.getClass().getSimpleName(), terminal.getType());
        Thread.ofPlatform().name("key-reader").daemon(true).start(this::readKeys);
    }

    private void readKeys() {
        try {
            NonBlockingReader reader = terminal.reader();
            while (!closing) {
                int c = reader.read(READ_TIMEOUT_MILLIS);
                if (c == NonBlockingReader.READ_EXPIRED) {
                    continue;
                }
                if (c < 0) {
                    break;
                }
                events.publish(new Event.KeyPressed((char) c));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Key reader ended", e);
        }
        if (!closing) {
            events.publish(new Event.InputClosed());
        }
    }

    @Override
    public synchronized void close() {
        if (terminal == null) {
            return;
        }
        closing = true;
        try {
            terminal.setAttributes(originalAttributes);
            terminal.close();
        } catch (IOException | RuntimeException e) {
            log.debug("Could not restore the terminal", e);
        }
        terminal = null;
    }
}
