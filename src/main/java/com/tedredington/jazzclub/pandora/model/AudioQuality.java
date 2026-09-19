package com.tedredington.jazzclub.pandora.model;

import java.util.Arrays;
import java.util.Optional;

/** The {@code audio_quality} setting and the key it selects in Pandora's {@code audioUrlMap}. */
public enum AudioQuality {

    LOW("low", "lowQuality"),
    MEDIUM("medium", "mediumQuality"),
    HIGH("high", "highQuality");

    private final String configValue;
    private final String apiKey;

    AudioQuality(String configValue, String apiKey) {
        this.configValue = configValue;
        this.apiKey = apiKey;
    }

    public static Optional<AudioQuality> fromConfigValue(String value) {
        return Arrays.stream(values()).filter(q -> q.configValue.equals(value)).findFirst();
    }

    public String configValue() {
        return configValue;
    }

    public String apiKey() {
        return apiKey;
    }
}
