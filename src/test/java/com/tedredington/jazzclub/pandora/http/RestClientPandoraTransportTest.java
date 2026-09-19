package com.tedredington.jazzclub.pandora.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.net.URI;

import com.tedredington.jazzclub.pandora.error.PandoraTransportException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientPandoraTransportTest {

    private static final URI URI_WITH_ENCODED_TOKEN =
            URI.create("https://tuner.example/services/json/?method=user.getStationList&auth_token=a%2Bb%2Fc%3D");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClientPandoraTransport transport = new RestClientPandoraTransport(builder.build());

    @Test
    void postsTheBodyAsPlainTextWithoutReEncodingTheQuery() {
        server.expect(requestTo(URI_WITH_ENCODED_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("deadbeef"))
                .andRespond(withSuccess("{\"stat\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertThat(transport.post(URI_WITH_ENCODED_TOKEN, "deadbeef")).isEqualTo("{\"stat\":\"ok\"}");
        server.verify();
    }

    @Test
    void anEmptyResponseBodyBecomesAnEmptyString() {
        server.expect(requestTo(URI_WITH_ENCODED_TOKEN)).andRespond(withSuccess());

        assertThat(transport.post(URI_WITH_ENCODED_TOKEN, "x")).isEmpty();
    }

    @Test
    void httpErrorsBecomeTransportExceptionsNamingTheStatus() {
        server.expect(requestTo(URI_WITH_ENCODED_TOKEN)).andRespond(withServerError());

        assertThatThrownBy(() -> transport.post(URI_WITH_ENCODED_TOKEN, "x"))
                .isInstanceOf(PandoraTransportException.class)
                .hasMessageContaining("500");
    }

    @Test
    void networkFailuresNameTheHostButNeverTheTokenBearingUrl() {
        server.expect(requestTo(URI_WITH_ENCODED_TOKEN)).andRespond(withException(new SocketTimeoutException("slow")));

        assertThatThrownBy(() -> transport.post(URI_WITH_ENCODED_TOKEN, "x"))
                .isInstanceOf(PandoraTransportException.class)
                .hasMessageContaining("tuner.example")
                .hasMessageNotContaining("auth_token")
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void theAuthTokenCannotLeakThroughALoggedStackTrace() {
        server.expect(requestTo(URI_WITH_ENCODED_TOKEN)).andRespond(withException(new SocketTimeoutException("slow")));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> transport.post(URI_WITH_ENCODED_TOKEN, "x"));

        // what "log.debug(msg, exception)" would write at -vv
        StringWriter stackTrace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(stackTrace));
        assertThat(stackTrace.toString()).doesNotContain("auth_token").doesNotContain("a%2Bb").contains("slow");
    }
}
