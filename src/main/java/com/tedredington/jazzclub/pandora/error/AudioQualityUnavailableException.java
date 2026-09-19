package com.tedredington.jazzclub.pandora.error;

import com.tedredington.jazzclub.pandora.model.AudioQuality;

/** The configured {@code audio_quality} is not offered for this account (e.g. "high" without a subscription). */
public final class AudioQualityUnavailableException extends PandoraException {

    private final AudioQuality quality;

    public AudioQualityUnavailableException(AudioQuality quality) {
        super("Selected audio quality is not available: " + quality.configValue());
        this.quality = quality;
    }

    public AudioQuality quality() {
        return quality;
    }
}
