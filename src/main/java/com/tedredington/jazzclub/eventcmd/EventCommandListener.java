package com.tedredington.jazzclub.eventcmd;

import java.time.Duration;

import com.tedredington.jazzclub.app.event.PlayerEvent;
import com.tedredington.jazzclub.config.JazzclubProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Connects the player's events to the user's event script. One Spring {@code @EventListener} among
 * possibly many: a desktop notification or a built-in scrobbler would be another class like this one,
 * with no change to the player.
 */
@Component
class EventCommandListener {

    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DRAIN_ON_EXIT = Duration.ofSeconds(5);

    private final EventCommandRunner runner;

    EventCommandListener(JazzclubProperties properties) {
        String command = properties.eventCommand();
        this.runner = command == null || command.isBlank()
                ? null
                : new EventCommandRunner(expandHome(command.strip()), SCRIPT_TIMEOUT, DRAIN_ON_EXIT);
    }

    @EventListener
    void on(PlayerEvent event) {
        if (runner != null) {
            runner.submit(event);
        }
    }

    @PreDestroy
    void close() {
        if (runner != null) {
            runner.close();
        }
    }

    /** No shell is involved, so {@code ~} would not expand by itself; config files are full of it. */
    static String expandHome(String path) {
        return path.startsWith("~/") ? System.getProperty("user.home") + path.substring(1) : path;
    }
}
