package com.tedredington.jazzclub.config;

import java.util.concurrent.ScheduledExecutorService;

import com.tedredington.jazzclub.app.PlayerLoop;
import com.tedredington.jazzclub.terminal.TerminalSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Starts the player once the context is up, and reports its result as the process exit code.
 * Does nothing unless {@code jazzclub.interactive} is set, which only {@code main} does; a
 * {@code @SpringBootTest} therefore never logs in or grabs the terminal.
 */
@Component
class JazzclubRunner implements ApplicationRunner, ExitCodeGenerator {

    private final JazzclubProperties properties;
    private final ObjectProvider<PlayerLoop> playerLoop;
    private final ObjectProvider<TerminalSession> terminalSession;
    private final ObjectProvider<ScheduledExecutorService> ticker;
    private int exitCode;

    JazzclubRunner(JazzclubProperties properties, ObjectProvider<PlayerLoop> playerLoop,
                   ObjectProvider<TerminalSession> terminalSession,
                   ObjectProvider<ScheduledExecutorService> ticker) {
        this.properties = properties;
        this.playerLoop = playerLoop;
        this.terminalSession = terminalSession;
        this.ticker = ticker;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.interactive()) {
            return;
        }
        terminalSession.getObject().open();
        ticker.getObject();
        exitCode = playerLoop.getObject().run();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
