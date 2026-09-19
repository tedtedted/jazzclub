package com.tedredington.jazzclub.pandora.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PandoraErrorCodeTest {

    @Test
    void everyCodeRoundTrips() {
        Arrays.stream(PandoraErrorCode.values())
                .filter(c -> c != PandoraErrorCode.UNKNOWN)
                .forEach(c -> assertThat(PandoraErrorCode.fromCode(c.code())).isEqualTo(c));
    }

    @Test
    void unlistedCodesMapToUnknown() {
        assertThat(PandoraErrorCode.fromCode(31337)).isEqualTo(PandoraErrorCode.UNKNOWN);
    }

    @Test
    void codesWithoutPianobarWordingStillSayWhichCodeItWas() {
        assertThat(PandoraErrorCode.RATE_LIMIT.message()).isEqualTo("Access denied. Try again later.");
        assertThat(PandoraErrorCode.DEVICE_DISABLED.message()).contains("DEVICE_DISABLED");
    }
}
