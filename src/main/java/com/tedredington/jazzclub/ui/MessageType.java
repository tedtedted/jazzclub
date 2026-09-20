package com.tedredington.jazzclub.ui;

/** pianobar's message classes with their default prefixes. All but {@link #NONE} first clear the current line. */
public enum MessageType {

    NONE("none", "", false),
    INFO("info", "(i) ", true),
    PLAYING("nowplaying", "|>  ", true),
    TIME("time", "#   ", true),
    ERROR("err", "/!\\ ", true),
    QUESTION("question", "[?] ", true),
    LIST("list", "\t", true);

    private final String configName;
    private final String prefix;
    private final boolean clearsLine;

    MessageType(String configName, String prefix, boolean clearsLine) {
        this.configName = configName;
        this.prefix = prefix;
        this.clearsLine = clearsLine;
    }

    /** The part after {@code format_msg_} in the config file. */
    public String configName() {
        return configName;
    }

    public String prefix() {
        return prefix;
    }

    public boolean clearsLine() {
        return clearsLine;
    }
}
