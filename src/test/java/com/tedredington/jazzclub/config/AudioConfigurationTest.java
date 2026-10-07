package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tedredington.jazzclub.app.PlayerLoop;
import com.tedredington.jazzclub.player.AudioPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

class AudioConfigurationTest {

    @TempDir
    Path directory;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan("com.tedredington.jazzclub.app.action")
    static class Actions {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AppConfiguration.class, AudioConfiguration.class,
                        TerminalConfiguration.class, PandoraConfiguration.class, CredentialsConfiguration.class,
                        PlayerLifecycle.class, Actions.class)
                .withPropertyValues("jazzclub.config-file=" + directory.resolve("config"),
                        "jazzclub.state-file=" + directory.resolve("state"), "jazzclub.decoder=ffmpeg");
    }

    @Test
    void assemblingAndInspectingThePlayerDoesNotPrepareADecoderOrItsResources() {
        runner().run(context -> {
            assertThat(context.getBean(PlayerLoop.class)).isNotNull();
            AudioPlayer player = context.getBean(AudioPlayer.class);
            player.setVolume(-5);
            assertThat(player.volume()).isEqualTo(-5);
            assertThat(player.isActive()).isFalse();
            player.stop();
            var factory = context.getSourceApplicationContext().getBeanFactory();
            assertThat(factory.containsSingleton("decoderFactory")).isFalse();
            assertThat(factory.containsSingleton("streamHttpClient")).isFalse();
            assertThat(factory.containsSingleton("downloadWatchdog")).isFalse();
        });
    }

    @Test
    void contextCloseShutsDownAllocatedNetworkAndWatchdogResources() {
        runner().run(context -> {
            HttpClient pandora = context.getBean("pandoraHttpClient", HttpClient.class);
            HttpClient stream = context.getBean("streamHttpClient", HttpClient.class);
            ScheduledExecutorService watchdog = context.getBean("downloadWatchdog", ScheduledExecutorService.class);
            watchdog.schedule(() -> { }, 1, TimeUnit.HOURS);
            context.close();
            assertThat(pandora.awaitTermination(Duration.ofSeconds(2))).isTrue();
            assertThat(stream.awaitTermination(Duration.ofSeconds(2))).isTrue();
            assertThat(watchdog.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        });
    }
}
