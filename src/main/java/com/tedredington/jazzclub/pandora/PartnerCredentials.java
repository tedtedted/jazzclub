package com.tedredington.jazzclub.pandora;

import java.util.Objects;

/**
 * Identifies the client <em>application</em> to Pandora, as opposed to the listener.
 *
 * @param encryptKey Blowfish key for request bodies (pianobar: {@code encrypt_password})
 * @param decryptKey Blowfish key for the server's sync time (pianobar: {@code decrypt_password})
 */
public record PartnerCredentials(String user, String password, String deviceModel,
                                 String encryptKey, String decryptKey) {

    /** The publicly known "android" partner that pianobar ships as its default. */
    public static final PartnerCredentials ANDROID = new PartnerCredentials(
            "android", "AC7IBG09A3DTSYM4R41UJWL07VLN8JI7", "android-generic", "6#26FRL$ZWD", "R=U!LH$O2B#");

    public PartnerCredentials {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(deviceModel, "deviceModel");
        Objects.requireNonNull(encryptKey, "encryptKey");
        Objects.requireNonNull(decryptKey, "decryptKey");
    }
}
