package com.tedredington.jazzclub.pandora.error;

/**
 * Root of everything that can go wrong while talking to Pandora. Sealed, so callers can
 * handle failures exhaustively with a pattern-matching {@code switch}.
 */
public abstract sealed class PandoraException extends RuntimeException
        permits PandoraApiException, InvalidLoginException, AudioQualityUnavailableException,
                PandoraProtocolException, PandoraTransportException {

    protected PandoraException(String message) {
        super(message);
    }

    protected PandoraException(String message, Throwable cause) {
        super(message, cause);
    }
}
