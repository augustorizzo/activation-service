package br.com.desafio.activation.infrastructure.integration;

import br.com.desafio.activation.application.integration.ActivationCommand;
import br.com.desafio.activation.application.integration.ActivationProvider;
import br.com.desafio.activation.application.integration.ProviderResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class HttpActivationProvider implements ActivationProvider {

    private final RestClient restClient;

    @Autowired
    public HttpActivationProvider(
            @Value("${provider.base-url}") String baseUrl,
            @Value("${provider.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${provider.read-timeout-ms}") int readTimeoutMs
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    HttpActivationProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProviderResult activate(ActivationCommand command) {
        try {
            return restClient.post()
                    .uri("/provider/v1/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", command.externalId())
                    .body(command)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();

                        if (status >= 200 && status < 300) {
                            return ProviderResult.SUCCESS;
                        }

                        if (status == 429 || status >= 500) {
                            return ProviderResult.TEMPORARY_FAILURE;
                        }

                        return ProviderResult.PERMANENT_FAILURE;
                    });
        } catch (ResourceAccessException ex) {
            // Uma falha de transporte não garante que a ativação não ocorreu.
            return ProviderResult.UNKNOWN_OUTCOME;
        }
    }
}