package com.tedredington.jazzclub.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import com.tedredington.jazzclub.network.ProxySettings;
import com.tedredington.jazzclub.pandora.PartnerCredentials;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Connection settings for Pandora. Defaults equal pianobar's; the property names follow its
 * config keys ({@code rpc_host}, {@code rpc_tls_port}, {@code timeout}, {@code proxy}, {@code partner_user}, ...).
 */
@ConfigurationProperties("jazzclub.pandora")
public record PandoraProperties(
        @DefaultValue("tuner.pandora.com") String rpcHost,
        @DefaultValue("443") int rpcTlsPort,
        // a bare number means seconds, as in pianobar's "timeout = 30"
        @DefaultValue("30") @DurationUnit(ChronoUnit.SECONDS) Duration timeout,
        String proxy,
        String controlProxy,
        String bindTo,
        Path caBundle,
        @DefaultValue Partner partner) {

    public PandoraProperties {
        if (rpcHost.isBlank()) {
            throw new IllegalArgumentException("rpc_host must not be empty");
        }
        if (rpcTlsPort < 1 || rpcTlsPort > 65_535) {
            throw new IllegalArgumentException("rpc_tls_port must be between 1 and 65535, was " + rpcTlsPort);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, was " + timeout);
        }
    }

    /**
     * The proxy for talking to Pandora: {@code control_proxy} if set, else {@code proxy}, else the
     * {@code http_proxy} environment variable, as in pianobar.
     *
     * @param environmentProxy the value of {@code http_proxy}, may be {@code null}
     * @return {@code null} for a direct connection
     */
    public ProxySettings apiProxy(String environmentProxy) {
        if (isSet(controlProxy)) {
            return ProxySettings.parse(controlProxy, "control_proxy");
        }
        return streamProxy(environmentProxy);
    }

    /** The proxy for fetching audio, which {@code control_proxy} deliberately does not cover. */
    public ProxySettings streamProxy(String environmentProxy) {
        if (isSet(proxy)) {
            return ProxySettings.parse(proxy, "proxy");
        }
        return isSet(environmentProxy) ? ProxySettings.parse(environmentProxy, "http_proxy") : null;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    public URI baseUri() {
        return URI.create("https://" + rpcHost + ":" + rpcTlsPort);
    }

    public record Partner(
            @DefaultValue("android") String user,
            @DefaultValue("AC7IBG09A3DTSYM4R41UJWL07VLN8JI7") String password,
            @DefaultValue("android-generic") String device,
            @DefaultValue("6#26FRL$ZWD") String encryptPassword,
            @DefaultValue("R=U!LH$O2B#") String decryptPassword) {

        public PartnerCredentials toCredentials() {
            return new PartnerCredentials(user, password, device, encryptPassword, decryptPassword);
        }
    }
}
