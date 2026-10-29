package br.com.desafio.activation.application;

import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.domain.exception.ActivationRequestNotFoundException;
import br.com.desafio.activation.domain.exception.DuplicateExternalIdException;
import br.com.desafio.activation.infrastructure.persistence.ActivationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {

    private final ActivationRequestRepository repository;

    public ActivationRequestService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivationRequest create(
            String externalId,
            String customerId,
            String productCode
    ) {
        if (repository.findByExternalId(externalId).isPresent()) {
            throw new DuplicateExternalIdException(externalId);
        }

        var request = new ActivationRequest(
                externalId,
                customerId,
                productCode
        );

        return repository.saveAndFlush(request);
    }

    @Transactional(readOnly = true)
    public ActivationRequest findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    @Transactional
    public ActivationRequest startProcessing(UUID id) {
        var request = repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));

        request.startProcessing();

        return repository.saveAndFlush(request);
    }

    @Transactional
    public ActivationRequest finishProcessing(UUID id, boolean successful) {
        var request = repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));

        if (successful) {
            request.complete();
        } else {
            request.fail();
        }

        return repository.saveAndFlush(request);
    }
}