package com.tedredington.jazzclub.credentials;

/** Credentials could not be obtained. The message is written for the person at the terminal. */
public class CredentialsException extends RuntimeException {

    public CredentialsException(String message) {
        super(message);
    }

    public CredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
