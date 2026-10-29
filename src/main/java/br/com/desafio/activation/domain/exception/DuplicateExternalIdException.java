package br.com.desafio.activation.domain.exception;

public class DuplicateExternalIdException extends RuntimeException {

    public DuplicateExternalIdException(String externalId) {
        super("Já existe uma solicitação com o externalId: " + externalId);
    }
}