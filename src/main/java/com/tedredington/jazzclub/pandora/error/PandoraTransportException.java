package com.tedredington.jazzclub.pandora.error;

/** The request never produced a usable HTTP response: DNS, TLS, timeout, non-2xx status. */
public final class PandoraTransportException extends PandoraException {

    public PandoraTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
