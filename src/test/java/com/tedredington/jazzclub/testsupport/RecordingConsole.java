package com.tedredington.jazzclub.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;

/** Remembers what was printed, rendered with pianobar's prefixes but without ANSI codes. */
public final class RecordingConsole implements Console {

    public record Message(MessageType type, String text) {
    }

    private final List<Message> messages = new ArrayList<>();

    @Override
    public synchronized void print(MessageType type, String text) {
        messages.add(new Message(type, text));
    }

    public synchronized List<Message> messages() {
        return List.copyOf(messages);
    }

    /** Everything printed so far, as the user would read it. */
    public synchronized String output() {
        StringBuilder out = new StringBuilder();
        messages.forEach(m -> out.append(m.type().prefix()).append(m.text()));
        return out.toString();
    }

    public synchronized void clear() {
        messages.clear();
    }
}
