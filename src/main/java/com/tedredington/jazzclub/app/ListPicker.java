package com.tedredington.jazzclub.app;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.MessageType;
import com.tedredington.jazzclub.ui.Prompter;

/**
 * pianobar's numbered menu for anything but stations: artists, songs, genres. A number selects, any
 * other text narrows the list to entries containing it, an empty line backs out.
 */
public final class ListPicker {

    private final Console console;
    private final Prompter prompter;

    public ListPicker(Console console, Prompter prompter) {
        this.console = console;
        this.prompter = prompter;
    }

    /** @param label what to show, and what the filter is matched against */
    public <T> Optional<T> pick(List<T> items, Function<T, String> label, String prompt) {
        return pick(items, (index, item) -> "%2d) %s".formatted(index, label.apply(item)), label, prompt);
    }

    /**
     * @param line       renders a whole entry including its number, for lists with a user-defined format
     * @param filterText what the filter is matched against
     */
    public <T> Optional<T> pick(List<T> items, BiFunction<Integer, T, String> line, Function<T, String> filterText,
                                String prompt) {
        String filter = "";
        while (true) {
            for (int i = 0; i < items.size(); i++) {
                if (filterText.apply(items.get(i)).toLowerCase(Locale.ROOT).contains(filter)) {
                    console.list(line.apply(i, items.get(i)) + "\n");
                }
            }
            console.print(MessageType.QUESTION, prompt);
            Optional<String> answer = prompter.readLine();
            if (answer.isEmpty()) {
                return Optional.empty();
            }
            String input = answer.get().strip();
            if (isIndex(input) && Integer.parseInt(input) < items.size()) {
                return Optional.of(items.get(Integer.parseInt(input)));
            }
            filter = input.toLowerCase(Locale.ROOT);
        }
    }

    static boolean isIndex(String input) {
        return !input.isEmpty() && input.length() <= 6 && input.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
