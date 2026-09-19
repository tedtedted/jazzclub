package com.tedredington.jazzclub.pandora.model;

import java.util.Arrays;

/** Stream encodings Pandora serves. {@code MP3} only appears for subscribers on high quality. */
public enum AudioEncoding {

    AAC_PLUS("aacplus"),
    MP3("mp3"),
    UNKNOWN("");

    private final String apiValue;

    AudioEncoding(String apiValue) {
        this.apiValue = apiValue;
    }

    public static AudioEncoding fromApiValue(String value) {
        return Arrays.stream(values())
                .filter(e -> e != UNKNOWN && e.apiValue.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
