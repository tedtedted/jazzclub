package com.tedredington.jazzclub.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProxySettingsTest {

    @Test
    void parsesPianobarsDocumentedForm() {
        ProxySettings proxy = ProxySettings.parse("http://user:password@proxy.example:3128/", "proxy");

        assertThat(proxy).isEqualTo(new ProxySettings("proxy.example", 3128, "user", "password"));
        assertThat(proxy.hasCredentials()).isTrue();
    }

    @Test
    void credentialsAndSchemeAreOptional() {
        assertThat(ProxySettings.parse("http://127.0.0.1:9090/", "control_proxy"))
                .isEqualTo(new ProxySettings("127.0.0.1", 9090, null, null));
        assertThat(ProxySettings.parse(" proxy.example:8080 ", "proxy"))
                .isEqualTo(new ProxySettings("proxy.example", 8080, null, null));
    }

    @Test
    void withoutAPortCurlsDefaultApplies() {
        assertThat(ProxySettings.parse("http://proxy.example", "proxy").port()).isEqualTo(1080);
    }

    @Test
    void aUserWithoutPasswordAndAPasswordWithColonsBothWork() {
        assertThat(ProxySettings.parse("http://me@p.example:1", "proxy").password()).isEmpty();
        assertThat(ProxySettings.parse("http://me:a:b:c@p.example:1", "proxy").password()).isEqualTo("a:b:c");
    }

    @Test
    void otherProxyTypesAreRefusedWithAReasonNamingTheSetting() {
        assertThatThrownBy(() -> ProxySettings.parse("socks5://127.0.0.1:9050", "control_proxy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("control_proxy is invalid: only http:// proxies are supported, not socks5://");
    }

    @Test
    void nonsenseIsRefusedWithoutEchoingWhatMightContainAPassword() {
        assertThatThrownBy(() -> ProxySettings.parse("http://user:s3cret@ bad host/", "proxy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("proxy is invalid")
                .hasMessageNotContaining("s3cret");
        assertThatThrownBy(() -> ProxySettings.parse("http:///nohost", "proxy"))
                .hasMessageContaining("names no host");
    }

    @Test
    void thePasswordIsMaskedInToStringButPresentForFfmpegsEnvironment() {
        ProxySettings proxy = ProxySettings.parse("http://me:hunter2@p.example:3128", "proxy");

        assertThat(proxy.toString()).contains("me:****@p.example:3128").doesNotContain("hunter2");
        assertThat(proxy.toEnvironmentValue()).isEqualTo("http://me:hunter2@p.example:3128/");
        assertThat(ProxySettings.parse("p.example:1", "proxy").toEnvironmentValue()).isEqualTo("http://p.example:1/");
        assertThat(ProxySettings.parse("p.example:1", "proxy").toString()).isEqualTo("ProxySettings[p.example:1]");
    }
}
