package com.tedredington.jazzclub.pandora;

import java.net.URI;

import com.tedredington.jazzclub.pandora.error.PandoraTransportException;

/** Moves one request body to Pandora and brings the response body back. Knows nothing about the protocol. */
@FunctionalInterface
public interface PandoraTransport {

    /**
     * @param uri  fully built and already percent-encoded; implementations must not re-encode it
     * @param body plain JSON or hex cipher text
     * @return the raw response body
     * @throws PandoraTransportException if no successful HTTP response was received
     */
    String post(URI uri, String body);
}
