package com.tedredington.jazzclub.pandora.http;

import java.net.URI;

import com.tedredington.jazzclub.pandora.PandoraTransport;
import com.tedredington.jazzclub.pandora.error.PandoraTransportException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** {@link PandoraTransport} on top of Spring's {@link RestClient}. */
public final class RestClientPandoraTransport implements PandoraTransport {

    private final RestClient restClient;

    public RestClientPandoraTransport(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String post(URI uri, String body) {
        try {
            String response = restClient.post()
                    .uri(uri) // a URI, not a template: the query is already encoded
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return response == null ? "" : response;
        } catch (RestClientResponseException e) {
            throw new PandoraTransportException("Pandora answered with HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // The URI must not leak: its query string carries the auth token, and Spring puts the whole
            // URI into this exception's message. So keep only the underlying I/O problem as the cause.
            Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
            throw new PandoraTransportException("Could not reach Pandora at " + uri.getHost()
                    + (root != e ? " (" + root.getClass().getSimpleName() + ")" : ""), root != e ? root : null);
        }
    }
}
