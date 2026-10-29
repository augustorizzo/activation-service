package br.com.desafio.activation.application.integration;

public interface ActivationProvider {

    ProviderResult activate(ActivationCommand command);
}