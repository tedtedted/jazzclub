package com.tedredington.jazzclub.config.file;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of reading a config file: its entries in file order, plus anything worth telling the user.
 *
 * @param entries  later duplicates win, as in pianobar
 * @param warnings human-readable, already prefixed with file name and line number where applicable
 */
public record ParsedConfig(Map<String, String> entries, List<String> warnings) {

    public static final ParsedConfig EMPTY = new ParsedConfig(Map.of(), List.of());

    public ParsedConfig {
        entries = Map.copyOf(entries);
        warnings = List.copyOf(warnings);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }
}
