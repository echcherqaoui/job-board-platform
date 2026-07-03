package com.echcherqaoui.jobboard.exception.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Error codes shared across all services.
 * Each service defines its own enum implementing IErrorCode for domain-specific codes.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements IErrorCode {

    VALIDATION_FAILED("REQ_400", "Input validation failed", 400),

    UNAUTHORIZED("AUTH_401", "Authentication required", 401),

    INVALID_SIGNATURE("SEC_403", "Security violation: Invalid HMAC signature. [EventID: %s]", 403),
    FORBIDDEN("GEN_403", "Access denied", 403),

    INTERNAL_SERVER_ERROR("GEN_500", "An unexpected error occurred [ID: %s]", 500),
    EVENT_PROCESSING_FAILED("PROCESSING_500", "Failed to process event '%s' at offset %d", 500),
    NO_HANDLER_FOUND("HANDLER_500", "No handler structurally bound to schema descriptor '%s' at offset %d", 500),
    DESERIALIZATION_FAILED("DESERIALIZATION_500", "Deserialization failed for record at offset %d. Poison pill suspected.", 500),;

    private final String code;
    private final String message;
    private final int httpStatus;
}