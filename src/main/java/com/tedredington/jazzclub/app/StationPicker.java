package com.tedredington.jazzclub.app;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.tedredington.jazzclub.pandora.model.Station;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;
import com.tedredington.jazzclub.ui.Renderer;

/**
 * pianobar's station menu: a numbered list in the order of the {@code sort} setting. Typing a number selects; typing anything
 * else narrows the list to names containing it, and a single remaining match is selected by itself.
 */
public final class StationPicker {

    private final Console console;
    private final Prompter prompter;
    private final Renderer renderer;
    private final Comparator<Station> order;
    private final boolean autoselect;

    /** @param autoselect pianobar's {@code autoselect}: whether the plain station menu takes a single match by itself */
    public StationPicker(Console console, Prompter prompter, Renderer renderer, Comparator<Station> order,
                         boolean autoselect) {
        this.console = console;
        this.prompter = prompter;
        this.renderer = renderer;
        this.order = order;
        this.autoselect = autoselect;
    }

    /** @return empty if the user backed out with an empty line, or there is nothing to choose from */
    public Optional<Station> pick(List<Station> stations, String prompt) {
        return pick(() -> stations, prompt, autoselect, input -> false);
    }

    /**
     * @param stations   read again before every listing, so a {@code command} may change what is shown
     * @param autoselect take a single remaining filter match without asking
     * @param command    sees every input that is not a station number; returns {@code true} if it
     *                   handled it, otherwise the input becomes the filter
     */
    public Optional<Station> pick(Supplier<List<Station>> stations, String prompt, boolean autoselect,
                                  Predicate<String> command) {
        if (stations.get().isEmpty()) {
            console.error("No station available.\n");
            return Optional.empty();
        }

        String filter = "";
        while (true) {
            List<Station> sorted = stations.get().stream().sorted(order).toList();
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
            if (autoselect && matches == 1 && sorted.size() != 1) {
                console.append(lastMatch + "\n");
                return Optional.of(sorted.get(lastMatch));
            }

            Optional<String> line = prompter.readLine();
            if (line.isEmpty()) {
                return Optional.empty();
            }
            String input = line.get().strip();
            if (ListPicker.isIndex(input) && Integer.parseInt(input) < sorted.size()) {
                return Optional.of(sorted.get(Integer.parseInt(input)));
            }
            filter = command.test(input) ? "" : input.toLowerCase(Locale.ROOT);
        }
    }
}
