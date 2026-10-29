package br.com.desafio.activation.domain.exception;

import java.util.UUID;

public class ActivationRequestNotFoundException extends RuntimeException {

    public ActivationRequestNotFoundException(UUID id) {
        super("Solicitação não encontrada para o id: " + id);
    }
}