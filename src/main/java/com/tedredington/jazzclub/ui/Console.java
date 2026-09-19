package com.tedredington.jazzclub.ui;

/** Everything jazzclub shows to the user goes through here. Text is printed as given: callers add newlines. */
public interface Console {

    void print(MessageType type, String text);

    default void info(String text) {
        print(MessageType.INFO, text);
    }

    default void error(String text) {
        print(MessageType.ERROR, text);
    }

    default void list(String text) {
        print(MessageType.LIST, text);
    }

    /** Continues a line started earlier, e.g. the "Ok." after "(i) Login... ". */
    default void append(String text) {
        print(MessageType.NONE, text);
    }
}
