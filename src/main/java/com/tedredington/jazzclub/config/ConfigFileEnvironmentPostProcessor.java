package com.tedredington.jazzclub.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tedredington.jazzclub.app.ActionId;
import com.tedredington.jazzclub.config.file.ConfigKey;
import com.tedredington.jazzclub.config.file.ParsedConfig;
import com.tedredington.jazzclub.config.file.UserConfigFile;
import com.tedredington.jazzclub.config.file.XdgDirectories;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Makes {@code ~/.config/jazzclub/config} a Spring property source, so the file's settings reach
 * {@code @ConfigurationProperties} like any other property.
 *
 * <p>Precedence, highest first: command line, system properties, environment variables, this file,
 * built-in defaults. Secrets are deliberately left out, see {@link ConfigKey#isSecret()}.
 */
public class ConfigFileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "jazzclubConfigFile";
    /** Overrides the file location, e.g. {@code --jazzclub.config-file=/tmp/test-config}. */
    static final String LOCATION_PROPERTY = "jazzclub.config-file";
    static final String KEYS_PROPERTY = "jazzclub.keys";

    private final Log log;
    private final XdgDirectories directories;

    public ConfigFileEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this(logFactory, XdgDirectories.system());
    }

    ConfigFileEnvironmentPostProcessor(DeferredLogFactory logFactory, XdgDirectories directories) {
        this.log = logFactory.getLog(ConfigFileEnvironmentPostProcessor.class);
        this.directories = directories;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = configFile(environment);
        ParsedConfig config = new UserConfigFile().load(file);
        config.warnings().forEach(log::warn);

        Map<String, Object> properties = new LinkedHashMap<>();
        // published so that beans needing the file itself (credentials) read the same one
        properties.put(LOCATION_PROPERTY, file.toString());
        config.entries().forEach((fileKey, value) -> {
            ConfigKey key = ConfigKey.fromFileKey(fileKey);
            if (ActionId.fromConfigKey(fileKey).isPresent()) {
                // bracket notation keeps the underscore and any odd character in the map key
                properties.put(KEYS_PROPERTY + "[" + fileKey + "]", value);
            } else if (key == null) {
                log.info("Ignoring unsupported setting '" + fileKey + "' in " + file);
            } else if (!key.isSecret()) {
                properties.put(key.property(), value);
            }
        });

        MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources()
                    .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addLast(source);
        }
    }

    private Path configFile(ConfigurableEnvironment environment) {
        String override = environment.getProperty(LOCATION_PROPERTY);
        return override != null && !override.isBlank() ? Path.of(override) : directories.configFile();
    }
}
