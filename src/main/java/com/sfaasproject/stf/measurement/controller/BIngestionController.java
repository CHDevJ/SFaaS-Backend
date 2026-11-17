package com.sfaasproject.stf.measurement.controller;

import com.sfaasproject.stf.common.*;
import com.sfaasproject.stf.measurement.dto.*;
import com.sfaasproject.stf.measurement.security.InternalApiKeyVerifier;
import com.sfaasproject.stf.operations.service.BIngestionApplicationService;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "stf.backend-b.ingestion-enabled", havingValue = "true")
public class BIngestionController {
    private final InternalApiKeyVerifier key;
    private final BIngestionApplicationService service;

    public BIngestionController(InternalApiKeyVerifier key, BIngestionApplicationService service) {
        this.key = key;
        this.service = service;
    }

    @PostMapping("/internal/v1/measurements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<IngestionAccepted> ingest(
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String supplied,
            @Valid @RequestBody MeasurementIngestionRequest request) {
        if (!key.matches(supplied))
            throw new DomainException(
                    HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "내부 API 키를 확인해 주세요.");
        return ApiResponse.ok(service.accept(request));
    }
}
