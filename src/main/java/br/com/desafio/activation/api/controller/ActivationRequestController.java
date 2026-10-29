package br.com.desafio.activation.api.controller;

import br.com.desafio.activation.api.dto.ActivationResponse;
import br.com.desafio.activation.api.dto.CreateActivationRequest;
import br.com.desafio.activation.application.ActivationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {

    private final ActivationApplicationService service;

    public ActivationRequestController(ActivationApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActivationResponse> create(
            @Valid @RequestBody CreateActivationRequest input
    ) {
        var request = service.create(
                input.externalId(),
                input.customerId(),
                input.productCode()
        );

        var location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(request.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(ActivationResponse.from(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivationResponse> findById(
            @PathVariable("id") UUID id
    ) {
        var request = service.findById(id);

        return ResponseEntity.ok(ActivationResponse.from(request));
    }
}