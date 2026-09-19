package com.tedredington.jazzclub.config.file;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses pianobar's config format: one {@code key = value} per line, {@code #} comments.
 *
 * <p>The details follow pianobar's {@code settings.c} so existing files behave identically. The key is
 * trimmed. Of the value, at most <em>one</em> leading whitespace character is dropped and trailing
 * whitespace is kept, because values such as {@code love_icon =  <3} or a key bound to the space bar
 * depend on it. This is also why {@link java.util.Properties} cannot be used here.
 */
public final class ConfigFileParser {

    public ParsedConfig parse(List<String> lines, String sourceName) {
        Map<String, String> entries = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        int lineNumber = 0;
        for (String rawLine : lines) {
            lineNumber++;
            String line = stripLineTerminator(rawLine);
            String content = line.stripLeading();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            int delimiter = line.indexOf('=');
            if (delimiter < 0) {
                warnings.add("Invalid line at " + sourceName + ":" + lineNumber);
                continue;
            }
            String key = line.substring(0, delimiter).strip();
            if (key.isEmpty()) {
                warnings.add("Missing key at " + sourceName + ":" + lineNumber);
                continue;
            }
            String value = line.substring(delimiter + 1);
            if (!value.isEmpty() && Character.isWhitespace(value.charAt(0))) {
                value = value.substring(1);
            }
            entries.put(key, value);
        }
        return new ParsedConfig(entries, warnings);
    }

    private static String stripLineTerminator(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\r' || line.charAt(end - 1) == '\n')) {
            end--;
        }
        return line.substring(0, end);
    }
}
