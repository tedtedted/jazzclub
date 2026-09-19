package com.tedredington.jazzclub.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.tedredington.jazzclub.ui.Prompter;

/** Answers prompts from a script. An exhausted script answers with an empty line. */
public final class ScriptedPrompter implements Prompter {

    private final Deque<String> answers = new ArrayDeque<>();
    private int prompts;

    public ScriptedPrompter answer(String... lines) {
        answers.addAll(List.of(lines));
        return this;
    }

    @Override
    public Optional<String> readLine() {
        prompts++;
        String next = answers.pollFirst();
        return next == null || next.isEmpty() ? Optional.empty() : Optional.of(next);
    }

    public int prompts() {
        return prompts;
    }
}
