package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.tedredington.jazzclub.pandora.model.AudioQuality;
import com.tedredington.jazzclub.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class JazzclubPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JazzclubProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsMatchPianobar() {
        runner.run(context -> {
            JazzclubProperties properties = context.getBean(JazzclubProperties.class);
            assertThat(properties.interactive()).isFalse();
            assertThat(properties.audioQuality()).isEqualTo(AudioQuality.HIGH);
            assertThat(properties.volume()).isZero();
            assertThat(properties.gainMul()).isEqualTo(1.0);
            assertThat(properties.history()).isEqualTo(5);
            assertThat(properties.maxRetry()).isEqualTo(3);
            assertThat(properties.autostartStation()).isNull();
            assertThat(properties.eventCommand()).isNull();
            assertThat(properties.fifo()).isNull();
            assertThat(properties.bufferSeconds()).isEqualTo(5);
            assertThat(properties.sampleRate()).isZero();
            assertThat(properties.audioPipe()).isNull();
            assertThat(properties.sort()).isEqualTo(com.tedredington.jazzclub.app.StationSort.NAME_AZ);
            assertThat(properties.autoselect()).isTrue();
            assertThat(properties.ffmpeg()).isEqualTo("ffmpeg");
            assertThat(properties.format()).isEqualTo(TestData.FORMAT);
            assertThat(properties.keys()).isEmpty();
        });
    }

    @Test
    void iconsKeepTheirLeadingSpaceAndKeysTheirOddCharacters() {
        // a real property source, because withPropertyValues() trims and the config file does not
        Map<String, Object> fromFile = Map.of(
                "jazzclub.format.love-icon", " <3!",
                "jazzclub.keys[act_songlove]", "l",
                "jazzclub.keys[act_songpausetoggle2]", " ",
                "jazzclub.volume", "-7");
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("file", fromFile)))
                .run(context -> {
                    JazzclubProperties properties = context.getBean(JazzclubProperties.class);
                    assertThat(properties.format().loveIcon()).isEqualTo(" <3!");
                    assertThat(properties.keys()).containsEntry("act_songlove", "l")
                            .containsEntry("act_songpausetoggle2", " ");
                    assertThat(properties.volume()).isEqualTo(-7);
                });
    }

    @Test
    void nonsenseValuesFailStartupWithAReadableReason() {
        runner.withPropertyValues("jazzclub.gain-mul=-1").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("gain_mul"));
        runner.withPropertyValues("jazzclub.history=-1").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("history"));
        runner.withPropertyValues("jazzclub.max-retry=0").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("max_retry"));
        runner.withPropertyValues("jazzclub.buffer-seconds=-1").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("buffer_seconds"));
        runner.withPropertyValues("jazzclub.sample-rate=7").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("sample_rate"));
        runner.withPropertyValues("jazzclub.audio-quality=ultra").run(context ->
                assertThat(context).hasFailed());
    }
}
