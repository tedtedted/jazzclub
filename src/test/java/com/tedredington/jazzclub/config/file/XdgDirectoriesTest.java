package com.tedredington.jazzclub.config.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class XdgDirectoriesTest {

    private static final Path HOME = Path.of("/home/ted");

    private static XdgDirectories with(Map<String, String> environment) {
        return new XdgDirectories(environment::get, HOME);
    }

    @Test
    void defaultsFollowTheSpecWhenNothingIsSet() {
        XdgDirectories directories = with(Map.of());

        assertThat(directories.configFile()).isEqualTo(Path.of("/home/ted/.config/jazzclub/config"));
        assertThat(directories.stateDirectory()).isEqualTo(Path.of("/home/ted/.local/state/jazzclub"));
    }

    @Test
    void environmentVariablesTakePrecedence() {
        XdgDirectories directories = with(Map.of("XDG_CONFIG_HOME", "/etc/xdg-ted", "XDG_STATE_HOME", "/var/state"));

        assertThat(directories.configDirectory()).isEqualTo(Path.of("/etc/xdg-ted/jazzclub"));
        assertThat(directories.stateDirectory()).isEqualTo(Path.of("/var/state/jazzclub"));
    }

    @Test
    void blankOrRelativeValuesAreIgnoredAsTheSpecDemands() {
        assertThat(with(Map.of("XDG_CONFIG_HOME", "  ")).configDirectory())
                .isEqualTo(Path.of("/home/ted/.config/jazzclub"));
        assertThat(with(Map.of("XDG_CONFIG_HOME", "relative/dir")).configDirectory())
                .isEqualTo(Path.of("/home/ted/.config/jazzclub"));
    }

    @Test
    void systemInstanceResolvesBelowTheRealHome() {
        assertThat(XdgDirectories.system().configFile().toString()).endsWith("jazzclub/config");
    }
}
