package com.tedredington.jazzclub.ui;

/** pianobar's message classes with their default prefixes. All but {@link #NONE} first clear the current line. */
public enum MessageType {

    NONE("", false),
    INFO("(i) ", true),
    PLAYING("|>  ", true),
    TIME("#   ", true),
    ERROR("/!\\ ", true),
    QUESTION("[?] ", true),
    LIST("\t", true);

    private final String prefix;
    private final boolean clearsLine;

    MessageType(String prefix, boolean clearsLine) {
        this.prefix = prefix;
        this.clearsLine = clearsLine;
    }

    public String prefix() {
        return prefix;
    }

    public boolean clearsLine() {
        return clearsLine;
    }
}
