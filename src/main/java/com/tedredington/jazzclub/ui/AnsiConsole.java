package com.tedredington.jazzclub.ui;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

/** Writes to a stream the way pianobar's {@code BarUiMsg} does. */
public final class AnsiConsole implements Console {

    private static final String CLEAR_LINE = "\033[2K";

    private static final String PLACEHOLDER = "%s";

    private final PrintStream out;
    private final Map<MessageType, String[]> wrappers = new EnumMap<>(MessageType.class);

    public AnsiConsole(PrintStream out) {
        this(out, Map.of());
    }

    /**
     * @param formats pianobar's {@code format_msg_*} settings by {@link MessageType#configName()}: text
     *                with one {@code %s} where the message goes. A value without it is ignored, as in
     *                pianobar, so a typo cannot swallow every message.
     */
    public AnsiConsole(PrintStream out, Map<String, String> formats) {
        this.out = out;
        for (MessageType type : MessageType.values()) {
            String format = formats.get(type.configName());
            int at = format == null ? -1 : format.indexOf(PLACEHOLDER);
            wrappers.put(type, at < 0
                    ? new String[] {type.prefix(), ""}
                    : new String[] {format.substring(0, at), format.substring(at + PLACEHOLDER.length())});
        }
    }

    @Override
    public synchronized void print(MessageType type, String text) {
        if (type.clearsLine()) {
            out.print(CLEAR_LINE);
        }
        String[] wrapper = wrappers.get(type);
        out.print(wrapper[0]);
        out.print(text);
        out.print(wrapper[1]);
        out.flush();
    }
}
