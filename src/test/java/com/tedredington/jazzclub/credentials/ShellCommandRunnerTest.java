package com.tedredington.jazzclub.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

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

    @Test
    @Timeout(10)
    void whateverTheShellStartedIsKilledWithIt() throws InterruptedException {
        ShellCommandRunner impatient = new ShellCommandRunner(Duration.ofMillis(300));

        // a pipeline forces the shell to fork instead of exec'ing, so "sleep" is a grandchild
        assertThatThrownBy(() -> impatient.run("sleep 97 | cat"))
                .isInstanceOf(CredentialsException.class);

        for (int i = 0; i < 40 && !sleepers().isEmpty(); i++) {
            Thread.sleep(50);
        }
        assertThat(sleepers()).as("orphaned 'sleep 97' processes").isEmpty();
    }

    /** Regression: with the pool-based reader, one orphan starved every later command on a 2-core CI runner. */
    @Test
    @Timeout(10)
    void aTimedOutCommandDoesNotBlockTheNextOne() {
        ShellCommandRunner impatient = new ShellCommandRunner(Duration.ofMillis(300));
        assertThatThrownBy(() -> impatient.run("sleep 98 | cat")).isInstanceOf(CredentialsException.class);

        assertThat(runner.run("echo next")).isEqualTo("next\n");
    }

    /** Real {@code sleep 97} processes; matching the executable, not any command line that mentions it. */
    private static List<String> sleepers() {
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c -> c.endsWith("/sleep")).orElse(false))
                .filter(p -> p.info().arguments().map(args -> List.of(args).contains("97")).orElse(false))
                .map(p -> p.pid() + " " + p.info().commandLine().orElse("?"))
                .toList();
    }
}
