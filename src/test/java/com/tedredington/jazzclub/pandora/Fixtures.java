package com.tedredington.jazzclub.pandora;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class Fixtures {

    private Fixtures() {
    }

    static String load(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/pandora/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No such fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String failure(int code) {
        return "{\"stat\":\"fail\",\"message\":\"An unexpected error occurred\",\"code\":" + code + "}";
    }
}
