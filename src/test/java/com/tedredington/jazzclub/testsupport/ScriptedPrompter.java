package com.tedredington.jazzclub.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.ui.Prompter;

/**
 * Answers prompts from a script, one entry per prompt whatever its kind. An exhausted script, an empty
 * entry, or an entry the prompt would not have accepted all count as "just pressed Enter".
 */
public final class ScriptedPrompter implements Prompter {

    private final Deque<String> answers = new ArrayDeque<>();
    private int prompts;

    public ScriptedPrompter answer(String... lines) {
        answers.addAll(List.of(lines));
        return this;
    }

    @Override
    public Optional<String> readLine(String allowedCharacters) {
        prompts++;
        String next = answers.pollFirst();
        if (next == null || next.isEmpty()) {
            return Optional.empty();
        }
        if (allowedCharacters != null && !next.chars().allMatch(c -> allowedCharacters.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(next);
    }

    @Override
    public Optional<Character> readChar(String allowedCharacters) {
        prompts++;
        String next = answers.pollFirst();
        if (next == null || next.length() != 1 || allowedCharacters.indexOf(next.charAt(0)) < 0) {
            return Optional.empty();
        }
        return Optional.of(next.charAt(0));
    }

    public int prompts() {
        return prompts;
    }
}
