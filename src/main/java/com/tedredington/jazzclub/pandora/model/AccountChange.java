package com.tedredington.jazzclub.pandora.model;

/**
 * Requested changes to the account; {@code null} leaves a setting alone.
 * {@link #toString()} never shows the new password.
 */
public record AccountChange(String newUsername, String newPassword, Boolean explicitContentFilter) {

    public static final AccountChange NONE = new AccountChange(null, null, null);

    public boolean isEmpty() {
        return newUsername == null && newPassword == null && explicitContentFilter == null;
    }

    public AccountChange withUsername(String username) {
        return new AccountChange(username, newPassword, explicitContentFilter);
    }

    public AccountChange withPassword(String password) {
        return new AccountChange(newUsername, password, explicitContentFilter);
    }

    public AccountChange withExplicitContentFilter(boolean enabled) {
        return new AccountChange(newUsername, newPassword, enabled);
    }

    @Override
    public String toString() {
        return "AccountChange[newUsername=" + newUsername + ", newPassword=" + (newPassword == null ? "null" : "****")
                + ", explicitContentFilter=" + explicitContentFilter + "]";
    }
}
