package com.tedredington.jazzclub.config;

import java.time.Duration;

import com.tedredington.jazzclub.config.file.UserConfigFile;
import com.tedredington.jazzclub.credentials.CommandRunner;
import com.tedredington.jazzclub.credentials.ConfigFileCredentialsProvider;
import com.tedredington.jazzclub.credentials.CredentialsProvider;
import com.tedredington.jazzclub.credentials.ShellCommandRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JazzclubProperties.class)
class CredentialsConfiguration {

    /** Generous, because the command may be waiting for the user to type a passphrase. */
    private static final Duration PASSWORD_COMMAND_TIMEOUT = Duration.ofMinutes(2);

    @Bean
    CommandRunner commandRunner() {
        return new ShellCommandRunner(PASSWORD_COMMAND_TIMEOUT);
    }

    @Bean
    CredentialsProvider credentialsProvider(JazzclubProperties properties, CommandRunner commandRunner) {
        UserConfigFile file = new UserConfigFile();
        return new ConfigFileCredentialsProvider(
                () -> file.load(properties.configFile()), properties.configFile(), commandRunner);
    }
}
