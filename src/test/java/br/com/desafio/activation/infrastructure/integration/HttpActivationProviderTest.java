package br.com.desafio.activation.infrastructure.integration;

import br.com.desafio.activation.application.integration.ActivationCommand;
import br.com.desafio.activation.application.integration.ProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpActivationProviderTest {

    private static final String URL =
            "http://provider.test/provider/v1/activate";

    private static final ActivationCommand COMMAND =
            new ActivationCommand(
                    "EXT-PROVIDER-001",
                    "CUST-10001",
                    "INTERNET-500"
            );

    private MockRestServiceServer server;
    private HttpActivationProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder()
                .baseUrl("http://provider.test");

        server = MockRestServiceServer.bindTo(builder).build();
        provider = new HttpActivationProvider(builder.build());
    }

    @Test
    void shouldSendExpectedRequestAndReturnSuccess() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "EXT-PROVIDER-001"))
                .andExpect(content().json("""
                {
                  "externalId": "EXT-PROVIDER-001",
                  "customerId": "CUST-10001",
                  "productCode": "INTERNET-500"
                }
                """))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        var result = provider.activate(COMMAND);

        assertThat(result).isEqualTo(ProviderResult.SUCCESS);
        server.verify();
    }

    @Test
    void shouldReturnPermanentFailureForBadRequest() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        var result = provider.activate(COMMAND);

        assertThat(result).isEqualTo(ProviderResult.PERMANENT_FAILURE);
        server.verify();
    }

    @Test
    void shouldReturnTemporaryFailureForServerError() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var result = provider.activate(COMMAND);

        assertThat(result).isEqualTo(ProviderResult.TEMPORARY_FAILURE);
        server.verify();
    }

    @Test
    void shouldReturnUnknownOutcomeForTimeout() {
        server.expect(requestTo(URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Timeout simulado");
                });

        var result = provider.activate(COMMAND);

        assertThat(result).isEqualTo(ProviderResult.UNKNOWN_OUTCOME);
        server.verify();
    }

    @Test
    void shouldReturnTemporaryFailureForTooManyRequests() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        var result = provider.activate(COMMAND);

        assertThat(result).isEqualTo(ProviderResult.TEMPORARY_FAILURE);
        server.verify();
    }
}