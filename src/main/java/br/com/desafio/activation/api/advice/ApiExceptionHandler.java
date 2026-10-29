package br.com.desafio.activation.api.advice;

import br.com.desafio.activation.domain.exception.ActivationRequestNotFoundException;
import br.com.desafio.activation.domain.exception.DuplicateExternalIdException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;
import java.util.Objects;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String EXTERNAL_ID_CONSTRAINT =
            "uk_activation_requests_external_id";

    @ExceptionHandler(ActivationRequestNotFoundException.class)
    public ProblemDetail handleNotFound(
            ActivationRequestNotFoundException ex
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Solicitação não encontrada",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DuplicateExternalIdException.class)
    public ProblemDetail handleDuplicate(
            DuplicateExternalIdException ex
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Solicitação duplicada",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(
            DataIntegrityViolationException ex
    ) {
        if (isExternalIdConstraintViolation(ex)) {
            return problem(
                    HttpStatus.CONFLICT,
                    "Solicitação duplicada",
                    "Já existe uma solicitação com o externalId informado."
            );
        }

        log.error("Falha de integridade ao persistir solicitação", ex);

        return internalServerError();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var body = problem(
                HttpStatus.BAD_REQUEST,
                "Dados inválidos",
                "Um ou mais campos não passaram na validação."
        );

        var errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        Objects.requireNonNullElse(
                                error.getDefaultMessage(),
                                "Valor inválido"
                        )
                ))
                .toList();

        body.setProperty("errors", errors);

        return handleExceptionInternal(
                ex,
                body,
                headers,
                status,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar requisição", ex);

        return internalServerError();
    }

    private boolean isExternalIdConstraintViolation(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                if (constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT)
                        .contains(EXTERNAL_ID_CONSTRAINT)) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private ProblemDetail internalServerError() {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno",
                "Não foi possível processar a requisição."
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail
    ) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }

    public record FieldValidationError(
            String field,
            String message
    ) {
    }
}