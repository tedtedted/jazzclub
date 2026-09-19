package com.tedredington.jazzclub.app;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import com.tedredington.jazzclub.ui.Renderer;

/**
 * pianobar's station menu: a numbered list sorted by name. Typing a number selects; typing anything
 * else narrows the list to names containing it, and a single remaining match is selected by itself.
 */
public final class StationPicker {

    private final Console console;
    private final Prompter prompter;
    private final Renderer renderer;

    public StationPicker(Console console, Prompter prompter, Renderer renderer) {
        this.console = console;
        this.prompter = prompter;
        this.renderer = renderer;
    }

    /** @return empty if the user backed out with an empty line, or there is nothing to choose from */
    public Optional<Station> pick(List<Station> stations, String prompt) {
        if (stations.isEmpty()) {
            console.error("No station available.\n");
            return Optional.empty();
        }
        List<Station> sorted = stations.stream()
                .sorted(Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT)))
                .toList();

        String filter = "";
        while (true) {
            int matches = 0;
            int lastMatch = -1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).name().toLowerCase(Locale.ROOT).contains(filter)) {
                    console.list(renderer.stationListEntry(i, sorted.get(i)) + "\n");
                    matches++;
                    lastMatch = i;
                }
            }
            console.print(MessageType.QUESTION, prompt);
            if (matches == 1 && sorted.size() != 1) {
                console.append(lastMatch + "\n");
                return Optional.of(sorted.get(lastMatch));
            }

            Optional<String> line = prompter.readLine();
            if (line.isEmpty()) {
                return Optional.empty();
            }
            String input = line.get().strip();
            Optional<Station> byNumber = parseIndex(input).filter(i -> i < sorted.size()).map(sorted::get);
            if (byNumber.isPresent()) {
                return byNumber;
            }
            filter = input.toLowerCase(Locale.ROOT);
        }
    }

    private static Optional<Integer> parseIndex(String input) {
        if (input.isEmpty() || input.length() > 6 || !input.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(input));
    }
}
