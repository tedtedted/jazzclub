package com.tedredington.jazzclub.ui;

import java.util.Optional;

/** Asks the user for input while the player keeps running. */
public interface Prompter {

    /**
     * Reads one line, echoing what is typed and honouring backspace.
     *
     * @return the text, or empty if the user just pressed Enter or input ended
     */
    Optional<String> readLine();
}
