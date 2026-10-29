package br.com.desafio.activation.infrastructure.persistence;

import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.domain.ActivationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ActivationRequestRepositoryTest {

    @Autowired
    private ActivationRequestRepository repository;

    // A consulta antecipa a resposta de duplicidade.
    // A constraint única no banco garante a regra sob concorrência.
    @Test
    void shouldSaveAndFindRequestByExternalId() {
        var request = new ActivationRequest(
                "EXT-12345",
                "CUST-10001",
                "INTERNET-500"
        );

        repository.saveAndFlush(request);

        var found = repository.findByExternalId("EXT-12345")
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getExternalId()).isEqualTo("EXT-12345");
        assertThat(found.getCustomerId()).isEqualTo("CUST-10001");
        assertThat(found.getProductCode()).isEqualTo("INTERNET-500");
        assertThat(found.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }


    @Test
    void shouldRejectDuplicateExternalId() {
        var firstRequest = new ActivationRequest(
                "EXT-DUPLICATE",
                "CUST-10001",
                "INTERNET-500"
        );

        repository.saveAndFlush(firstRequest);

        var duplicateRequest = new ActivationRequest(
                "EXT-DUPLICATE",
                "CUST-10002",
                "INTERNET-300"
        );

        assertThatThrownBy(() -> repository.saveAndFlush(duplicateRequest))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}