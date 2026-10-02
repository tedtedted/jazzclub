package com.tedredington.jazzclub.lastfm;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Last.fm settings. The password is not among them: like Pandora's, it is a secret and is read from the
 * config file directly, never through Spring's {@code Environment}.
 *
 * @param user      {@code lastfm_user}: blank turns scrobbling off
 * @param apiKey    jazzclub's own API account, from {@code application.properties}; not a user setting
 * @param apiSecret the shared secret that goes with {@code apiKey}
 * @param endpoint  the API root, replaceable for tests
 * @param love      {@code lastfm_love}: pass loves and bans on Pandora on to Last.fm
 */
@ConfigurationProperties("jazzclub.lastfm")
public record LastFmProperties(
        String user,
        String apiKey,
        String apiSecret,
        @DefaultValue("https://ws.audioscrobbler.com/2.0/") URI endpoint,
        @DefaultValue("true") boolean love) {

    boolean isEnabled() {
        return user != null && !user.isBlank();
    }

    boolean hasApiAccount() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }
}
