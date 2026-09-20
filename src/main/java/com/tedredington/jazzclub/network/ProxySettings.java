package com.tedredington.jazzclub.network;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * An HTTP proxy as written in pianobar's {@code proxy} and {@code control_proxy}:
 * {@code http://user:password@host:port/}. The password never appears in {@link #toString()}.
 *
 * @param username {@code null} if the proxy needs no login
 */
public record ProxySettings(String host, int port, String username, String password) {

    /** What curl, and therefore pianobar, assumes when the URL names no port. */
    private static final int DEFAULT_PORT = 1080;

    /**
     * @param setting which config key the text came from, for the error message
     * @throws IllegalArgumentException with a message fit for the user
     */
    public static ProxySettings parse(String url, String setting) {
        String text = url.strip();
        URI uri;
        try {
            uri = new URI(text.contains("://") ? text : "http://" + text);
        } catch (URISyntaxException e) {
            throw invalid(setting, url, "not a valid URL");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw invalid(setting, url, "only http:// proxies are supported, not " + uri.getScheme() + "://");
        }
        if (uri.getHost() == null) {
            throw invalid(setting, url, "it names no host");
        }
        String username = null;
        String password = null;
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            username = parts[0];
            password = parts.length > 1 ? parts[1] : "";
        }
        return new ProxySettings(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT, username, password);
    }

    private static IllegalArgumentException invalid(String setting, String url, String reason) {
        // the URL may contain a password, so it is not echoed back
        return new IllegalArgumentException(setting + " is invalid: " + reason);
    }

    public boolean hasCredentials() {
        return username != null;
    }

    /** As an {@code http_proxy} environment value, for child processes like ffmpeg. */
    public String toEnvironmentValue() {
        String login = hasCredentials() ? username + ":" + password + "@" : "";
        return "http://" + login + host + ":" + port + "/";
    }

    @Override
    public String toString() {
        return "ProxySettings[" + (hasCredentials() ? username + ":****@" : "") + host + ":" + port + "]";
    }
}
