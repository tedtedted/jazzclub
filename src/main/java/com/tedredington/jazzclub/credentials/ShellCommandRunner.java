package com.tedredington.jazzclub.credentials;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs commands through {@code /bin/sh -c}, like pianobar. Standard input and error stay connected to
 * the terminal so that tools such as {@code gpg} or {@code pass} can ask for a passphrase.
 */
public final class ShellCommandRunner implements CommandRunner {

    private final Duration timeout;

    public ShellCommandRunner(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public String run(String command) {
        Process process;
        try {
            process = new ProcessBuilder("/bin/sh", "-c", command)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            throw new CredentialsException("Could not start password_command: " + e.getMessage(), e);
        }
        // Drain stdout concurrently: reading first would defeat the timeout, waiting first would
        // deadlock on a command that fills the pipe.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new CredentialsException("password_command did not finish within " + timeout.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new CredentialsException("password_command failed with exit status " + process.exitValue());
            }
            return output.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CredentialsException("Interrupted while waiting for password_command", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new CredentialsException("Could not read the output of password_command", e);
        } finally {
            process.destroyForcibly();
        }
    }

    private static String readAll(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
