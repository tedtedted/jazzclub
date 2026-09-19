package com.tedredington.jazzclub.config;

import java.net.URI;
import java.time.Duration;

import com.tedredington.jazzclub.pandora.PartnerCredentials;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for Pandora. Defaults equal pianobar's; the property names follow its
 * config keys ({@code rpc_host}, {@code rpc_tls_port}, {@code timeout}, {@code partner_user}, ...).
 */
@ConfigurationProperties("jazzclub.pandora")
public record PandoraProperties(
        @DefaultValue("tuner.pandora.com") String rpcHost,
        @DefaultValue("443") int rpcTlsPort,
        @DefaultValue("30s") Duration timeout,
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
