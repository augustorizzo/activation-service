package br.com.desafio.activation.application.integration;

public record ActivationCommand(
        String externalId,
        String customerId,
        String productCode
) {
}