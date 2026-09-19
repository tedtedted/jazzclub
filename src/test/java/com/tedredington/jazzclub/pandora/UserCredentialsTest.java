package com.tedredington.jazzclub.pandora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class UserCredentialsTest {

    @Test
    void toStringMasksThePasswordSoItCannotLeakIntoLogs() {
        assertThat(new UserCredentials("me@example.com", "hunter2").toString())
                .contains("me@example.com")
                .doesNotContain("hunter2");
    }

    @Test
    void bothFieldsAreRequired() {
        assertThatNullPointerException().isThrownBy(() -> new UserCredentials(null, "x"));
        assertThatNullPointerException().isThrownBy(() -> new UserCredentials("x", null));
    }
}
