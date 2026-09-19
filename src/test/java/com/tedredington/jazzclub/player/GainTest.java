package com.tedredington.jazzclub.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GainTest {

    @Test
    void addsVolumeAndScaledTrackGain() {
        assertThat(Gain.combinedDb(0, -6.0, 1.0)).isEqualTo(-6.0);
        assertThat(Gain.combinedDb(-3, -6.0, 0.5)).isEqualTo(-6.0);
        assertThat(Gain.combinedDb(5, -6.0, 0.0)).isEqualTo(5.0);
    }
}
