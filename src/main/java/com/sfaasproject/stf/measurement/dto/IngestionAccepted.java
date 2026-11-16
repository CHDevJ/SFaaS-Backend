package com.sfaasproject.stf.measurement.dto;

import java.time.OffsetDateTime;

public record IngestionAccepted(String messageId, OffsetDateTime acceptedAt, boolean duplicate) { }
