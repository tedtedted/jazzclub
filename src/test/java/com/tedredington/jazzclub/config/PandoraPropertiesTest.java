package com.tedredington.jazzclub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.tedredington.jazzclub.pandora.PartnerCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PandoraPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PandoraProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsMatchPianobar() {
        runner.run(context -> {
            PandoraProperties properties = context.getBean(PandoraProperties.class);
            assertThat(properties.baseUri()).hasToString("https://tuner.pandora.com:443");
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.partner().toCredentials()).isEqualTo(PartnerCredentials.ANDROID);
        });
    }

    @Test
    void everythingCanBeOverridden() {
        runner.withPropertyValues(
                        "jazzclub.pandora.rpc-host=internal-tuner.pandora.com",
                        "jazzclub.pandora.rpc-tls-port=8443",
                        "jazzclub.pandora.timeout=5s",
                        "jazzclub.pandora.partner.user=pandora one",
                        "jazzclub.pandora.partner.device=D01")
                .run(context -> {
                    PandoraProperties properties = context.getBean(PandoraProperties.class);
                    assertThat(properties.baseUri()).hasToString("https://internal-tuner.pandora.com:8443");
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.partner().user()).isEqualTo("pandora one");
                    assertThat(properties.partner().device()).isEqualTo("D01");
                    // untouched values keep their defaults
                    assertThat(properties.partner().encryptPassword()).isEqualTo(PartnerCredentials.ANDROID.encryptKey());
                });
    }

    @Test
    void nonsenseValuesFailStartupWithAReadableReason() {
        runner.withPropertyValues("jazzclub.pandora.rpc-tls-port=70000").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("rpc_tls_port must be between"));
        runner.withPropertyValues("jazzclub.pandora.timeout=0s").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("timeout must be positive"));
        runner.withPropertyValues("jazzclub.pandora.rpc-host= ").run(context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("rpc_host"));
    }
}
