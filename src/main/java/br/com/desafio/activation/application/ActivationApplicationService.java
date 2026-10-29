package br.com.desafio.activation.application;

import br.com.desafio.activation.application.integration.ActivationCommand;
import br.com.desafio.activation.application.integration.ActivationProvider;
import br.com.desafio.activation.application.integration.ProviderResult;
import br.com.desafio.activation.domain.ActivationRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ActivationApplicationService {

    private final ActivationRequestService requestService;
    private final ActivationProvider provider;

    public ActivationApplicationService(
            ActivationRequestService requestService,
            ActivationProvider provider
    ) {
        this.requestService = requestService;
        this.provider = provider;
    }

    public ActivationRequest create(
            String externalId,
            String customerId,
            String productCode
    ) {
        var created = requestService.create(
                externalId,
                customerId,
                productCode
        );

        var processing = requestService.startProcessing(created.getId());

        var result = provider.activate(new ActivationCommand(
                processing.getExternalId(),
                processing.getCustomerId(),
                processing.getProductCode()
        ));

        return requestService.finishProcessing(
                processing.getId(),
                result == ProviderResult.SUCCESS
        );
    }

    public ActivationRequest findById(UUID id) {
        return requestService.findById(id);
    }
}