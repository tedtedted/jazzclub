package com.tedredington.jazzclub.pandora.error;

/** The response could not be understood: malformed JSON, missing fields, undecryptable payload. */
public final class PandoraProtocolException extends PandoraException {

    public PandoraProtocolException(String message) {
        super(message);
    }

    public PandoraProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
