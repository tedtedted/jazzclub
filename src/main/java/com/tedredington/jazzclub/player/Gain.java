package com.tedredington.jazzclub.player;

/** pianobar's loudness formula: the user's volume plus the track's ReplayGain scaled by {@code gain_mul}. */
public final class Gain {

    private Gain() {
    }

    public static double combinedDb(int volumeDb, double trackGainDb, double gainMultiplier) {
        return volumeDb + trackGainDb * gainMultiplier;
    }
}
