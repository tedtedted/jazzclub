package com.tedredington.jazzclub.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ShellCommandRunnerTest {

    private final ShellCommandRunner runner = new ShellCommandRunner(Duration.ofSeconds(10));

    @Test
    void capturesStandardOutput() {
        assertThat(runner.run("printf 'line one\\nline two\\n'")).isEqualTo("line one\nline two\n");
    }

    @Test
    void runsThroughAShellSoPipesWork() {
        assertThat(runner.run("echo hunter2 | tr a-z A-Z")).isEqualTo("HUNTER2\n");
    }

    @Test
    void largeOutputDoesNotDeadlock() {
        assertThat(runner.run("head -c 300000 /dev/zero | tr '\\0' x")).hasSize(300_000);
    }

    @Test
    void aNonZeroExitStatusIsAnError() {
        assertThatThrownBy(() -> runner.run("exit 3"))
                .isInstanceOf(CredentialsException.class).hasMessageContaining("exit status 3");
    }

    @Test
    @Timeout(5)
    void aHangingCommandIsKilledAfterTheTimeout() {
        ShellCommandRunner impatient = new ShellCommandRunner(Duration.ofMillis(300));

        assertThatThrownBy(() -> impatient.run("sleep 30"))
                .isInstanceOf(CredentialsException.class).hasMessageContaining("did not finish");
    }
}
