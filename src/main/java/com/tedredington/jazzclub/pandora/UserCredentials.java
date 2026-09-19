package com.tedredington.jazzclub.pandora;

import java.util.Objects;

/** The listener's Pandora account. The password never appears in {@link #toString()}, so it cannot leak into logs. */
public record UserCredentials(String username, String password) {

    public UserCredentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "UserCredentials[username=" + username + ", password=****]";
    }
}
