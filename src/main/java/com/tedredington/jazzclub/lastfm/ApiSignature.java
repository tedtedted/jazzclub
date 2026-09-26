package com.tedredington.jazzclub.lastfm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Last.fm's {@code api_sig}: every parameter except {@code format} and {@code callback}, sorted by name,
 * concatenated as name and value without separators, followed by the shared secret, MD5, lower-case hex.
 */
final class ApiSignature {

    private ApiSignature() {
    }

    static String sign(Map<String, String> parameters, String secret) {
        StringBuilder text = new StringBuilder();
        new TreeMap<>(parameters).forEach((name, value) -> {
            if (!name.equals("format") && !name.equals("callback")) {
                text.append(name).append(value);
            }
        });
        text.append(secret);
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has MD5", e);
        }
    }
}
