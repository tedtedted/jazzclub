package com.tedredington.jazzclub.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class HttpClientFactoryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * Pretends to be an HTTP proxy far enough to see what the client asks of it: challenges once for
     * Basic authentication, then hangs up. Nothing is ever forwarded.
     */
    private static final class FakeProxy implements AutoCloseable {
        final ServerSocket server;
        final List<List<String>> requests = new CopyOnWriteArrayList<>();
        final boolean challenge;

        FakeProxy(boolean challenge) throws IOException {
            this.challenge = challenge;
            this.server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
            Thread.ofPlatform().daemon(true).start(this::serve);
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    List<String> head = new ArrayList<>();
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        head.add(line);
                    }
                    requests.add(head);
                    boolean authorised = head.stream().anyMatch(h -> h.toLowerCase().startsWith("proxy-authorization:"));
                    String response = challenge && !authorised
                            ? "HTTP/1.1 407 Proxy Authentication Required\r\n"
                            + "Proxy-Authenticate: Basic realm=\"test\"\r\nContent-Length: 0\r\n\r\n"
                            : "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    // the server socket was closed, or the client hung up; either ends this exchange
                }
            }
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    private FakeProxy proxy;

    @AfterEach
    void stopProxy() throws IOException {
        if (proxy != null) {
            proxy.close();
        }
    }

    private static void tryToReachPandora(HttpClient client) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://tuner.pandora.com/services/json/"))
                .timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        try {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            // expected: the fake proxy never builds the tunnel
        }
    }

    @Test
    void requestsGoThroughTheProxyAsAnHttpsTunnel() throws IOException {
        proxy = new FakeProxy(false);
        HttpClient client = HttpClientFactory.create(TIMEOUT,
                new ProxySettings("127.0.0.1", proxy.port(), null, null), null, null);

        tryToReachPandora(client);

        assertThat(proxy.requests).isNotEmpty();
        assertThat(proxy.requests.getFirst().getFirst()).isEqualTo("CONNECT tuner.pandora.com:443 HTTP/1.1");
        assertThat(proxy.requests.getFirst()).noneMatch(h -> h.toLowerCase().startsWith("proxy-authorization"));
    }

    @Test
    void proxyCredentialsAreSentWhenTheProxyAsksEvenForAnHttpsTunnel() throws IOException {
        proxy = new FakeProxy(true);
        HttpClient client = HttpClientFactory.create(TIMEOUT,
                new ProxySettings("127.0.0.1", proxy.port(), "me", "hunter2"), null, null);

        tryToReachPandora(client);

        String expected = "Basic " + Base64.getEncoder().encodeToString("me:hunter2".getBytes(StandardCharsets.UTF_8));
        assertThat(proxy.requests).as("challenge, then the authorised retry").hasSizeGreaterThanOrEqualTo(2);
        assertThat(proxy.requests.getLast())
                .anyMatch(h -> h.equalsIgnoreCase("Proxy-Authorization: " + expected));
    }

    @Test
    void theSwitchIsOnlySetWhenTheUserHasNotChosenOtherwise() {
        String key = "jdk.http.auth.tunneling.disabledSchemes";
        String before = System.getProperty(key);
        try {
            System.clearProperty(key);
            HttpClientFactory.allowProxyCredentialsForHttps();
            assertThat(System.getProperty(key)).isEmpty();

            System.setProperty(key, "Basic,NTLM");
            HttpClientFactory.allowProxyCredentialsForHttps();
            assertThat(System.getProperty(key)).isEqualTo("Basic,NTLM");
        } finally {
            if (before == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, before);
            }
        }
    }

    @Test
    void withoutSettingsAPlainClientIsBuilt() {
        HttpClient client = HttpClientFactory.create(TIMEOUT, null, null, null);

        assertThat(client.proxy()).isEmpty();
        assertThat(client.connectTimeout()).contains(TIMEOUT);
    }

    @Test
    void bindToAndCaBundleAreApplied() {
        HttpClient client = HttpClientFactory.create(TIMEOUT, null, "host!127.0.0.1",
                Path.of("src/test/resources/network/test-ca.pem"));

        assertThat(client.sslContext().getProtocol()).isEqualTo("TLS");
        assertThat(client).isNotNull();
    }

    @Test
    void badSettingsFailWhenTheClientIsBuiltNotOnTheFirstRequest() {
        assertThatThrownBy(() -> HttpClientFactory.create(TIMEOUT, null, "if!nope0", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bind_to");
        assertThatThrownBy(() -> HttpClientFactory.create(TIMEOUT, null, null, Path.of("/no/such/bundle.pem")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ca_bundle");
    }
}
