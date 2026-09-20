package com.tedredington.jazzclub.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

class CaBundleTest {

    private static final Path RESOURCES = Path.of("src/test/resources/network");

    @Test
    void aPemFileBecomesATlsContext() {
        SSLContext context = CaBundle.sslContext(RESOURCES.resolve("test-ca.pem"));

        assertThat(context.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void aBundleMayHoldSeveralAuthorities() {
        assertThat(CaBundle.sslContext(RESOURCES.resolve("two-cas.pem"))).isNotNull();
    }

    @Test
    void aMissingFileIsAReadableError() {
        assertThatThrownBy(() -> CaBundle.sslContext(RESOURCES.resolve("absent.pem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ca_bundle is invalid: cannot read");
    }

    @Test
    void aFileThatIsNotACertificateIsAReadableError() {
        assertThatThrownBy(() -> CaBundle.sslContext(RESOURCES.resolve("garbage.pem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("ca_bundle is invalid");
    }
}
