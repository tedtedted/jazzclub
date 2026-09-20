package com.tedredington.jazzclub.network;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

/** Builds the JDK HTTP client for talking to Pandora from the network-related settings. */
public final class HttpClientFactory {

    private static final String DISABLED_TUNNEL_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";

    private HttpClientFactory() {
    }

    /**
     * The JDK refuses Basic authentication towards a proxy when tunnelling HTTPS, which is the only
     * thing jazzclub does; a password in {@code proxy} would silently never be sent. The JDK reads the
     * switch once, when its HTTP classes load, so this must run first thing in {@code main}; the native
     * build passes the same property, as class initialisation may happen at build time there.
     * Credentials still only go to a proxy the user configured, and only when it asks.
     */
    public static void allowProxyCredentialsForHttps() {
        if (System.getProperty(DISABLED_TUNNEL_SCHEMES) == null) {
            System.setProperty(DISABLED_TUNNEL_SCHEMES, "");
        }
    }

    /**
     * @param proxy    may be {@code null}
     * @param bindTo   pianobar's {@code bind_to}, may be {@code null}
     * @param caBundle pianobar's {@code ca_bundle}, may be {@code null}
     * @throws IllegalArgumentException with a message fit for the user
     */
    public static HttpClient create(Duration connectTimeout, ProxySettings proxy, String bindTo, Path caBundle) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (proxy != null) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (proxy.hasCredentials()) {
                builder.authenticator(proxyAuthenticator(proxy));
            }
        }
        if (bindTo != null && !bindTo.isBlank()) {
            builder.localAddress(LocalAddress.resolve(bindTo));
        }
        if (caBundle != null) {
            builder.sslContext(CaBundle.sslContext(caBundle));
        }
        return builder.build();
    }

    private static Authenticator proxyAuthenticator(ProxySettings proxy) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // never hand the proxy password to a web server asking for credentials
                return getRequestorType() == RequestorType.PROXY
                        ? new PasswordAuthentication(proxy.username(), proxy.password().toCharArray())
                        : null;
            }
        };
    }
}
