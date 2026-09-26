package com.tedredington.jazzclub.lastfm;

import java.util.Objects;

/**
 * Permission to scrobble for one Last.fm user. The key never appears in {@link #toString()}.
 *
 * @param user the user name as Last.fm spells it, which may differ in case from the config file
 */
record Session(String user, String key) {

    Session {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(key, "key");
    }

    /** Last.fm user names are not case-sensitive. */
    boolean belongsTo(String configuredUser) {
        return user.equalsIgnoreCase(configuredUser.strip());
    }

    @Override
    public String toString() {
        return "Session[user=" + user + ", key=****]";
    }
}
