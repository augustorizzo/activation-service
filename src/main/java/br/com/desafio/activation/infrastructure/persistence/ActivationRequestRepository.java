package br.com.desafio.activation.infrastructure.persistence;

import br.com.desafio.activation.domain.ActivationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository
        extends JpaRepository<ActivationRequest, UUID> {

    Optional<ActivationRequest> findByExternalId(String externalId);
}