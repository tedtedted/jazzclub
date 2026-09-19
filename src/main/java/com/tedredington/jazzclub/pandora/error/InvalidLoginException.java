package com.tedredington.jazzclub.pandora.error;

/** The user's email address or password was rejected. */
public final class InvalidLoginException extends PandoraException {

    public InvalidLoginException() {
        super("Wrong email address or password.");
    }
}
