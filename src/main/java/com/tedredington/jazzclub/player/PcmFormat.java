package com.tedredington.jazzclub.player;

/** The raw audio passed from decoder to sink: signed 16 bit, little-endian, stereo, at this rate. */
public record PcmFormat(int sampleRate) {

    public static final int CHANNELS = 2;
    public static final int BYTES_PER_SAMPLE = 2;
    private static final int PANDORAS_RATE = 44_100;

    public PcmFormat {
        if (sampleRate < 8_000 || sampleRate > 192_000) {
            throw new IllegalArgumentException("sample_rate must be between 8000 and 192000, was " + sampleRate);
        }
    }

    /** @param configured pianobar's {@code sample_rate}; 0 means "as streamed", which for Pandora is 44.1 kHz */
    public static PcmFormat of(int configured) {
        return new PcmFormat(configured == 0 ? PANDORAS_RATE : configured);
    }

    public int bytesPerSecond() {
        return sampleRate * CHANNELS * BYTES_PER_SAMPLE;
    }
}
