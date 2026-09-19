package com.tedredington.jazzclub.ui;

import java.io.PrintStream;

/** Writes to a stream the way pianobar's {@code BarUiMsg} does. */
public final class AnsiConsole implements Console {

    private static final String CLEAR_LINE = "\033[2K";

    private final PrintStream out;

    public AnsiConsole(PrintStream out) {
        this.out = out;
    }

    @Override
    public synchronized void print(MessageType type, String text) {
        if (type.clearsLine()) {
            out.print(CLEAR_LINE);
        }
        out.print(type.prefix());
        out.print(text);
        out.flush();
    }
}
