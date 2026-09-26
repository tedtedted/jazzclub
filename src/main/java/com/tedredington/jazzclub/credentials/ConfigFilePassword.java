package com.tedredington.jazzclub.credentials;

import java.util.Optional;

import com.tedredington.jazzclub.config.file.ConfigKey;
import com.tedredington.jazzclub.config.file.ParsedConfig;

/**
 * pianobar's password rules, for any account in the config file: a non-empty plain password wins,
 * otherwise the first line printed by the password command. Shared by Pandora and Last.fm.
 */
public final class ConfigFilePassword {

    private final ConfigKey password;
    private final ConfigKey command;
    private final CommandRunner commandRunner;

    /**
     * @param password the key of the plain-text password, e.g. {@code password}
     * @param command  the key of the command that prints it, e.g. {@code password_command}
     */
    public ConfigFilePassword(ConfigKey password, ConfigKey command, CommandRunner commandRunner) {
        this.password = password;
        this.command = command;
        this.commandRunner = commandRunner;
    }

    /**
     * @return the password, or empty if neither key is set
     * @throws CredentialsException if the command fails or prints nothing
     */
    public Optional<String> find(ParsedConfig config) {
        String plain = config.get(password.fileKey()).orElse("");
        if (!plain.isEmpty()) {
            return Optional.of(plain);
        }
        String commandLine = config.get(command.fileKey()).orElse("");
        if (commandLine.isBlank()) {
            return Optional.empty();
        }
        String firstLine = commandRunner.run(commandLine).lines().findFirst().orElse("");
        if (firstLine.isEmpty()) {
            throw new CredentialsException(command.fileKey() + " printed nothing");
        }
        return Optional.of(firstLine);
    }
}
