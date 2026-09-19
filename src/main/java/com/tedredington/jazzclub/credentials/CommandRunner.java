package com.tedredington.jazzclub.credentials;

/** Runs a shell command and captures what it prints. Exists so {@code password_command} is testable. */
@FunctionalInterface
public interface CommandRunner {

    /**
     * @return the command's standard output
     * @throws CredentialsException if the command cannot be started, fails, or takes too long
     */
    String run(String command);
}
