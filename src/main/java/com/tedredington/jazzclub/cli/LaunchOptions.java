package com.tedredington.jazzclub.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the command line asked for, already validated.
 *
 * @param verbosity  0 quiet, 1 for {@code -v}, 2 or more for {@code -vv}
 * @param configFile {@code null} to use the XDG default
 */
public record LaunchOptions(int verbosity, Path configFile) {

    /** Compact, because log lines share the terminal with the player. */
    private static final String CONSOLE_PATTERN = "%d{HH:mm:ss} %-5level %logger{0}: %msg%n";

    /** The Spring properties these options translate to. */
    public Map<String, Object> toProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jazzclub.interactive", "true");
        properties.put("logging.pattern.console", CONSOLE_PATTERN);
        if (configFile != null) {
            properties.put("jazzclub.config-file", configFile.toAbsolutePath().toString());
        }
        if (verbosity == 1) {
            properties.put("logging.level.com.tedredington.jazzclub", "INFO");
        } else if (verbosity >= 2) {
            properties.put("logging.level.com.tedredington.jazzclub", "DEBUG");
            properties.put("logging.level.root", "INFO");
        }
        return properties;
    }
}
