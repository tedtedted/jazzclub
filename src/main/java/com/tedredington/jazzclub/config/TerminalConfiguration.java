package com.tedredington.jazzclub.config;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.config.file.XdgDirectories;
import com.tedredington.jazzclub.event.Event;
import com.tedredington.jazzclub.event.EventQueue;
import com.tedredington.jazzclub.remote.ControlFifo;
import com.tedredington.jazzclub.terminal.TerminalSession;
import com.tedredington.jazzclub.ui.AnsiConsole;
import com.tedredington.jazzclub.ui.Console;
import com.tedredington.jazzclub.ui.EventQueuePrompter;
import com.tedredington.jazzclub.ui.Prompter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Terminal input, output, and clock resources, acquired by the interactive runner. */
@Configuration(proxyBeanMethods = false)
class TerminalConfiguration {

    @Bean
    EventQueue eventQueue() {
        return new EventQueue();
    }

    @Bean
    Console console(JazzclubProperties properties) {
        return new AnsiConsole(System.out, properties.format().msg());
    }

    @Bean
    Prompter prompter(EventQueue events, Console console) {
        return new EventQueuePrompter(events, console);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    TerminalSession terminalSession(EventQueue events) {
        return new TerminalSession(events);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    ControlFifo controlFifo(JazzclubProperties properties, EventQueue events, Console console) {
        Path fifo = properties.fifo() != null
                ? properties.fifo()
                : XdgDirectories.system().configDirectory().resolve("ctl");
        return new ControlFifo(fifo, events, console);
    }

    @Bean(destroyMethod = "shutdownNow")
    @Lazy
    ScheduledExecutorService ticker(EventQueue events) {
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofPlatform().name("ticker").daemon(true).unstarted(runnable));
        ticker.scheduleAtFixedRate(() -> events.publish(new Event.Tick()), 1, 1, TimeUnit.SECONDS);
        return ticker;
    }
}
