package br.com.desafio.activation.api;

import br.com.desafio.activation.infrastructure.persistence.ActivationRequestRepository;
import br.com.desafio.activation.application.integration.ActivationProvider;
import br.com.desafio.activation.application.integration.ProviderResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.util.UUID;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:activation-api-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ActivationRequestApiTest {

    private static final String ENDPOINT = "/api/v1/requests";

    private static final String VALID_BODY = """
        {
          "externalId": "EXT-API-001",
          "customerId": "CUST-10001",
          "productCode": "INTERNET-500"
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivationRequestRepository repository;

    @MockitoBean
    private ActivationProvider provider;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        when(provider.activate(any()))
                .thenReturn(ProviderResult.SUCCESS);
    }

    @Test
    void shouldCreateRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.externalId").value("EXT-API-001"))
                .andExpect(jsonPath("$.customerId").value("CUST-10001"))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors", hasSize(3)));

        assertThat(repository.count()).isZero();
        verifyNoInteractions(provider);
    }

    @Test
    void shouldRejectDuplicateExternalId() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldFindExistingRequest() throws Exception {
        var result = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn();

        String location = result.getResponse().getHeader("Location");

        assertThat(location).isNotNull();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-API-001"))
                .andExpect(jsonPath("$.customerId").value("CUST-10001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get(
                        ENDPOINT + "/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title")
                        .value("Solicitação não encontrada"));
    }

    @ParameterizedTest
    @EnumSource(
            value = ProviderResult.class,
            names = {
                    "PERMANENT_FAILURE",
                    "TEMPORARY_FAILURE",
                    "UNKNOWN_OUTCOME"
            }
    )
    void shouldPersistFailedStatusWhenProviderDoesNotConfirmSuccess(
            ProviderResult providerResult
    ) throws Exception {
        when(provider.activate(any()))
                .thenReturn(providerResult);

        var result = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn();

        var saved = repository.findByExternalId("EXT-API-001")
                .orElseThrow();

        assertThat(saved.getStatus().name()).isEqualTo("FAILED");
        assertThat(repository.count()).isEqualTo(1);

        verify(provider, times(1)).activate(any());

        String location = result.getResponse().getHeader("Location");

        assertThat(location).isNotNull();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }
    @Test
    void shouldReturnProvidedCorrelationId() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header()
                        .string("X-Correlation-Id", correlationId));
    }
    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {
        var result = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String correlationId = result.getResponse()
                .getHeader("X-Correlation-Id");

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId).toString())
                .isEqualTo(correlationId);
    }
    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(repository.count()).isZero();
        verifyNoInteractions(provider);
    }
    @Test
    void shouldRejectMalformedId() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/id-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(provider);
    }
}