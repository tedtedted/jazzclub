package com.tedredington.jazzclub.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;

/**
 * Line input on top of the event queue. The terminal is in raw mode, so echo and backspace are
 * handled here. While a prompt is open, clock ticks are dropped (they would overwrite the prompt)
 * and everything else is set aside and replayed afterwards, so music changes wait for the answer
 * just like in pianobar.
 */
public final class EventQueuePrompter implements Prompter {

    private static final char BACKSPACE = '\b';
    private static final char DELETE = 127;
    private static final char CTRL_D = 4;
    private static final char CTRL_U = 21;

    private final EventQueue events;
    private final Console console;

    public EventQueuePrompter(EventQueue events, Console console) {
        this.events = events;
        this.console = console;
    }

    @Override
    public Optional<Character> readChar(String allowedCharacters) {
        List<Event> deferred = new ArrayList<>();
        try {
            while (true) {
                Event event = events.take();
                switch (event) {
                    case Event.KeyPressed(char key) -> {
                        if (key == '\r' || key == '\n' || key == CTRL_D) {
                            console.append("\n");
                            return Optional.empty();
                        }
                        boolean accepted = allowedCharacters == null
                                ? !Character.isISOControl(key)
                                : allowedCharacters.indexOf(key) >= 0;
                        if (accepted) {
                            console.append(key + "\n");
                            return Optional.of(key);
                        }
                    }
                    case Event.Tick tick -> {
                        // dropped
                    }
                    case Event.InputClosed closed -> {
                        deferred.add(closed);
                        console.append("\n");
                        return Optional.empty();
                    }
                    case Event.TrackFinished finished -> deferred.add(finished);
                    case Event.Notice notice -> deferred.add(notice);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            events.putBack(deferred);
        }
    }

    @Override
    public Optional<String> readLine(String allowedCharacters) {
        return readLine(allowedCharacters, true);
    }

    @Override
    public Optional<String> readSecret() {
        return readLine(null, false);
    }

    private Optional<String> readLine(String allowedCharacters, boolean echo) {
        StringBuilder line = new StringBuilder();
        List<Event> deferred = new ArrayList<>();
        try {
            while (true) {
                Event event = events.take();
                switch (event) {
                    case Event.KeyPressed(char key) -> {
                        if (key == '\r' || key == '\n' || key == CTRL_D) {
                            console.append("\n");
                            return line.isEmpty() ? Optional.empty() : Optional.of(line.toString());
                        }
                        edit(line, key, allowedCharacters, echo);
                    }
                    case Event.Tick tick -> {
                        // dropped
                    }
                    case Event.InputClosed closed -> {
                        deferred.add(closed);
                        console.append("\n");
                        return Optional.empty();
                    }
                    case Event.TrackFinished finished -> deferred.add(finished);
                    case Event.Notice notice -> deferred.add(notice);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            events.putBack(deferred);
        }
    }

    private void edit(StringBuilder line, char key, String allowedCharacters, boolean echo) {
        if (key == BACKSPACE || key == DELETE) {
            if (!line.isEmpty()) {
                line.setLength(line.length() - 1);
                if (echo) {
                    console.append("\b \b");
                }
            }
        } else if (key == CTRL_U) {
            if (echo) {
                console.append("\b \b".repeat(line.length()));
            }
            line.setLength(0);
        } else if (!Character.isISOControl(key)
                && (allowedCharacters == null || allowedCharacters.indexOf(key) >= 0)) {
            line.append(key);
            if (echo) {
                console.append(String.valueOf(key));
            }
        }
    }
}
