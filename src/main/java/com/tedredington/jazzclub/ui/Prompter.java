package com.tedredington.jazzclub.ui;

import java.util.Optional;

/** Asks the user for input while the player keeps running. */
public interface Prompter {

    /**
     * Reads one line, echoing what is typed and honouring backspace.
     *
     * @param allowedCharacters the only characters accepted, or {@code null} for any
     * @return the text, or empty if the user just pressed Enter or input ended
     */
    Optional<String> readLine(String allowedCharacters);

    default Optional<String> readLine() {
        return readLine(null);
    }

    /**
     * Reads a single key press, without waiting for Enter. Keys not in {@code allowedCharacters} are ignored.
     *
     * @return the key, or empty if the user pressed Enter or input ended
     */
    Optional<Character> readChar(String allowedCharacters);

    /** A number typed on a line of its own; empty for an empty line or a number too large to be an index. */
    default Optional<Integer> readNumber() {
        return readLine("0123456789").filter(digits -> digits.length() <= 6).map(Integer::parseInt);
    }

    /** {@code y} or {@code n} as a single key press; anything else, including Enter, means {@code defaultAnswer}. */
    default boolean confirm(boolean defaultAnswer) {
        return readChar("yYnN").map(answer -> answer == 'y' || answer == 'Y').orElse(defaultAnswer);
    }
}
