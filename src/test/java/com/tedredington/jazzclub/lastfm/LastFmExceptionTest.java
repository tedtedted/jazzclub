package com.tedredington.jazzclub.lastfm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LastFmExceptionTest {

    @ParameterizedTest(name = "error {0} is {1}")
    @CsvSource({
            "4, AuthenticationFailed",
            "9, AuthenticationFailed",
            "14, AuthenticationFailed",
            "8, TemporarilyUnavailable",
            "11, TemporarilyUnavailable",
            "16, TemporarilyUnavailable",
            "29, TemporarilyUnavailable",
            "6, Rejected",
            "10, Rejected",
            "13, Rejected",
            "26, Rejected",
    })
    void lastFmErrorCodesMapToWhatToDoAboutThem(int code, String type) {
        LastFmException e = LastFmException.of(code, "message");

        assertThat(e.getClass().getSimpleName()).isEqualTo(type);
        assertThat(e.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @CsvSource({"10, true", "13, true", "26, true", "6, false"})
    void aBadApiKeyIsTheBuildsFaultNotTheRequests(int code, boolean apiKeyProblem) {
        assertThat(((LastFmException.Rejected) LastFmException.of(code, "m")).isApiKeyProblem())
                .isEqualTo(apiKeyProblem);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', error 6", "'  Invalid parameters ', Invalid parameters"})
    void aMissingMessageIsReplacedByTheCode(String message, String expected) {
        assertThat(LastFmException.of(6, message)).hasMessage(expected);
    }
}
