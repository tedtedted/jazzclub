package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.tedredington.jazzclub.config.file.XdgDirectories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class ConfigFileEnvironmentPostProcessorTest {

    @TempDir
    Path home;

    private final StandardEnvironment environment = new StandardEnvironment();

    @BeforeEach
    void isolateFromTheBuildsSystemProperties() {
        // surefire pins jazzclub.config-file for every test as a safety net; here we test the lookup itself
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    }

    private void postProcess() {
        XdgDirectories directories = new XdgDirectories(name -> null, home);
        new ConfigFileEnvironmentPostProcessor(new DeferredLogs(), directories)
                .postProcessEnvironment(environment, new SpringApplication());
    }

    private Path writeXdgConfig(String content) throws IOException {
        Path file = home.resolve(".config/jazzclub/config");
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @Test
    void mapsPianobarKeysOntoSpringProperties() throws IOException {
        writeXdgConfig("audio_quality = low\nrpc_host = example.org\ntimeout = 12\ndevice = D01\n");

        postProcess();

        assertThat(environment.getProperty("jazzclub.audio-quality")).isEqualTo("low");
        assertThat(environment.getProperty("jazzclub.pandora.rpc-host")).isEqualTo("example.org");
        assertThat(environment.getProperty("jazzclub.pandora.timeout")).isEqualTo("12");
        assertThat(environment.getProperty("jazzclub.pandora.partner.device")).isEqualTo("D01");
    }

    @Test
    void credentialsNeverEnterTheEnvironment() throws IOException {
        writeXdgConfig("user = me@example.com\npassword = hunter2\npassword_command = pass show x\n");

        postProcess();

        PropertySource<?> source = environment.getPropertySources()
                .get(ConfigFileEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
        assertThat(source).isInstanceOf(MapPropertySource.class);
        assertThat(((MapPropertySource) source).getSource().toString())
                .doesNotContain("hunter2", "me@example.com", "pass show");
    }

    @Test
    void publishesWhichFileIsInEffect() throws IOException {
        Path file = writeXdgConfig("");

        postProcess();

        assertThat(environment.getProperty("jazzclub.config-file")).isEqualTo(file.toString());
    }

    @Test
    void theLocationCanBeOverridden() throws IOException {
        writeXdgConfig("audio_quality = low\n");
        Path other = Files.writeString(home.resolve("other"), "audio_quality = medium\n");
        environment.getPropertySources().addFirst(
                new MapPropertySource("commandLine", Map.of("jazzclub.config-file", other.toString())));

        postProcess();

        assertThat(environment.getProperty("jazzclub.audio-quality")).isEqualTo("medium");
    }

    @Test
    void commandLineAndEnvironmentBeatTheFileWhichBeatsDefaults() throws IOException {
        writeXdgConfig("audio_quality = low\nrpc_host = from-file\n");
        environment.getPropertySources().addFirst(
                new MapPropertySource("commandLine", Map.of("jazzclub.audio-quality", "high")));
        environment.getPropertySources().addLast(
                new MapPropertySource("defaults", Map.of("jazzclub.pandora.rpc-host", "from-defaults")));

        postProcess();

        assertThat(environment.getProperty("jazzclub.audio-quality")).isEqualTo("high");
        assertThat(environment.getProperty("jazzclub.pandora.rpc-host")).isEqualTo("from-file");
    }

    @Test
    void keyBindingsBecomeMapEntries() throws IOException {
        writeXdgConfig("act_songlove = l\nact_songban = disabled\n");

        postProcess();

        assertThat(environment.getProperty("jazzclub.keys[act_songlove]")).isEqualTo("l");
        assertThat(environment.getProperty("jazzclub.keys[act_songban]")).isEqualTo("disabled");
    }

    @Test
    void unsupportedKeysAreIgnored() throws IOException {
        writeXdgConfig("act_stationcreate = c\nevent_command = /bin/true\n");

        postProcess();

        assertThat(environment.getProperty("act_stationcreate")).isNull();
        assertThat(environment.getProperty("jazzclub.keys[act_stationcreate]")).isNull();
        assertThat(environment.getProperty("event_command")).isNull();
    }

    @Test
    void worksInAnEnvironmentWithoutTheStandardSources() throws IOException {
        writeXdgConfig("audio_quality = low\n");
        MockEnvironment bare = new MockEnvironment();

        new ConfigFileEnvironmentPostProcessor(new DeferredLogs(), new XdgDirectories(name -> null, home))
                .postProcessEnvironment(bare, new SpringApplication());

        assertThat(bare.getProperty("jazzclub.audio-quality")).isEqualTo("low");
    }

    @Test
    void aMissingFileLeavesEverythingAtItsDefault() {
        postProcess();

        assertThat(environment.getProperty("jazzclub.audio-quality")).isNull();
    }
}
