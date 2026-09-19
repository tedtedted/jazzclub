package com.tedredington.jazzclub.credentials;

import com.tedredington.jazzclub.pandora.UserCredentials;

/** Where the listener's Pandora login comes from. */
@FunctionalInterface
public interface CredentialsProvider {

    /** @throws CredentialsException if the username or password cannot be determined */
    UserCredentials credentials();
}
