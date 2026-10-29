package br.com.desafio.activation.api.dto;

import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.domain.ActivationStatus;

import java.time.Instant;
import java.util.UUID;

public record ActivationResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        ActivationStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ActivationResponse from(ActivationRequest request) {
        return new ActivationResponse(
                request.getId(),
                request.getExternalId(),
                request.getCustomerId(),
                request.getProductCode(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}