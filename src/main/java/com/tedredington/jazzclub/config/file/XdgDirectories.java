package com.tedredington.jazzclub.config.file;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Locations according to the XDG Base Directory specification. When a variable is unset the
 * spec's defaults apply on every platform, macOS included: terminal users expect {@code ~/.config},
 * and it is where pianobar looks too.
 */
public final class XdgDirectories {

    private static final String APPLICATION = "jazzclub";

    private final UnaryOperator<String> environment;
    private final Path home;

    public XdgDirectories(UnaryOperator<String> environment, Path home) {
        this.environment = environment;
        this.home = home;
    }

    public static XdgDirectories system() {
        return new XdgDirectories(System::getenv, Path.of(System.getProperty("user.home")));
    }

    /** {@code $XDG_CONFIG_HOME/jazzclub}, by default {@code ~/.config/jazzclub}. */
    public Path configDirectory() {
        return resolve("XDG_CONFIG_HOME", ".config");
    }

    /** {@code $XDG_STATE_HOME/jazzclub}, by default {@code ~/.local/state/jazzclub}. Logs and remembered state go here. */
    public Path stateDirectory() {
        return resolve("XDG_STATE_HOME", ".local/state");
    }

    /** {@code $XDG_CACHE_HOME/jazzclub}, by default {@code ~/.cache/jazzclub}. Safe to delete at any time. */
    public Path cacheDirectory() {
        return resolve("XDG_CACHE_HOME", ".cache");
    }

    public Path stateFile() {
        return stateDirectory().resolve("state");
    }

    public Path configFile() {
        return configDirectory().resolve("config");
    }

    private Path resolve(String variable, String defaultRelativeToHome) {
        String value = environment.apply(variable);
        // the spec says relative paths are invalid and must be ignored
        Path base = value != null && !value.isBlank() && Path.of(value).isAbsolute()
                ? Path.of(value)
                : home.resolve(defaultRelativeToHome);
        return base.resolve(APPLICATION);
    }
}
