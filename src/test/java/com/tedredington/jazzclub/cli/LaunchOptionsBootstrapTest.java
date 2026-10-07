package com.tedredington.jazzclub.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.tedredington.jazzclub.config.JazzclubProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class LaunchOptionsBootstrapTest {

    @TempDir
    Path directory;

    private final LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
    private LogLevel rootLevel;
    private LogLevel appLevel;

    @BeforeEach
    void rememberLoggingLevels() {
        rootLevel = logging.getLoggerConfiguration(LoggingSystem.ROOT_LOGGER_NAME).getConfiguredLevel();
        appLevel = logging.getLoggerConfiguration("com.tedredington.jazzclub").getConfiguredLevel();
    }

    @AfterEach
    void restoreLoggingLevels() {
        logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, rootLevel);
        logging.setLogLevel("com.tedredington.jazzclub", appLevel);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableConfigurationProperties(JazzclubProperties.class)
    static class Config {
    }

    @Test
    void explicitOptionsBeatSystemEnvironmentAndPackagedDefaultsBeforeBinding() throws Exception {
        Path cliFile = Files.writeString(directory.resolve("cli config"), "history = 17\n");
        Path otherFile = Files.writeString(directory.resolve("other"), "history = 23\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of(
                        "jazzclub.config-file", otherFile.toString(), "logging.level.root", "ERROR",
                        "jazzclub.state-file", directory.resolve("no-state").toString())));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("LOGGING_LEVEL_COM_TEDREDINGTON_JAZZCLUB", "WARN")));
        SpringApplication application = new SpringApplication(Config.class);
        application.setEnvironment(environment);

        try (var context = application.run(new LaunchOptions(2, cliFile).toSpringArguments())) {
            assertThat(context.getBean(JazzclubProperties.class).configFile()).isEqualTo(cliFile);
            assertThat(context.getBean(JazzclubProperties.class).history()).isEqualTo(17);
            assertThat(context.getEnvironment().getProperty("logging.level.root")).isEqualTo("INFO");
            assertThat(context.getEnvironment().getProperty("logging.level.com.tedredington.jazzclub"))
                    .isEqualTo("DEBUG");
        }
    }

    @Test
    void quietLaunchKeepsThePackagedLoggingDefault() throws Exception {
        Path config = Files.writeString(directory.resolve("config"), "");
        SpringApplication application = new SpringApplication(Config.class);
        application.setDefaultProperties(Map.of("jazzclub.state-file", directory.resolve("no-state").toString()));
        try (var context = application.run(new LaunchOptions(0, config).toSpringArguments())) {
            assertThat(context.getEnvironment().getProperty("logging.level.root")).isEqualTo("WARN");
        }
    }
}
