package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ApiSignatureTest {

    /** Worked out by hand: sorted names and values, secret appended, UTF-8, MD5. */
    @Test
    void signsSortedParametersFollowedByTheSecret() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("username", "ted");
        parameters.put("password", "pässword");
        parameters.put("method", "auth.getMobileSession");
        parameters.put("api_key", "KEY");

        assertThat(ApiSignature.sign(parameters, "SECRET")).isEqualTo("263be29a7aed6d47ef6115c2b676162d");
    }

    @Test
    void formatAndCallbackAreNotSigned() {
        Map<String, String> plain = Map.of("method", "track.scrobble", "api_key", "KEY");
        Map<String, String> withFormat = Map.of("method", "track.scrobble", "api_key", "KEY",
                "format", "json", "callback", "cb");

        assertThat(ApiSignature.sign(withFormat, "SECRET")).isEqualTo(ApiSignature.sign(plain, "SECRET"));
    }
}
