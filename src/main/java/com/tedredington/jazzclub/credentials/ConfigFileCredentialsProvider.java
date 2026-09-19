package com.tedredington.jazzclub.credentials;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.tedredington.jazzclub.config.file.ConfigKey;
import com.tedredington.jazzclub.config.file.ParsedConfig;
import com.tedredington.jazzclub.pandora.UserCredentials;

/**
 * Takes the login from the config file: {@code user}, then {@code password} or, if that is absent,
 * the first line printed by {@code password_command}. Same precedence as pianobar.
 */
public final class ConfigFileCredentialsProvider implements CredentialsProvider {

    private final Supplier<ParsedConfig> config;
    private final Path configFile;
    private final CommandRunner commandRunner;

    /**
     * @param config     read lazily and on every call, so an edited file is picked up on re-login
     * @param configFile only used to tell the user where to look
     */
    public ConfigFileCredentialsProvider(Supplier<ParsedConfig> config, Path configFile, CommandRunner commandRunner) {
        this.config = config;
        this.configFile = configFile;
        this.commandRunner = commandRunner;
    }

    @Override
    public UserCredentials credentials() {
        ParsedConfig parsed = config.get();
        String user = parsed.get(ConfigKey.USER.fileKey()).filter(v -> !v.isBlank())
                .orElseThrow(() -> new CredentialsException(
                        "No Pandora account configured. Add a line 'user = you@example.com' to " + configFile));
        return new UserCredentials(user.strip(), password(parsed));
    }

    private String password(ParsedConfig parsed) {
        String password = parsed.get(ConfigKey.PASSWORD.fileKey()).orElse("");
        if (!password.isEmpty()) {
            return password;
        }
        String command = parsed.get(ConfigKey.PASSWORD_COMMAND.fileKey()).orElse("");
        if (command.isBlank()) {
            throw new CredentialsException("No password configured. Add either 'password = ...' or "
                    + "'password_command = ...' to " + configFile);
        }
        String firstLine = commandRunner.run(command).lines().findFirst().orElse("");
        if (firstLine.isEmpty()) {
            throw new CredentialsException("password_command printed nothing");
        }
        return firstLine;
    }
}
