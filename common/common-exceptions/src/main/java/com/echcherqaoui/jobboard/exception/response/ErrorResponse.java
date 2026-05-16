package com.echcherqaoui.jobboard.exception.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
    private final String code;
    private final String message;
    private final Instant timestamp;
    private final String path;
    private Set<String> validationErrors;

    public ErrorResponse(String code, String message, String path) {
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        this.path = path;
    }

    public ErrorResponse setValidationErrors(Set<String> validationErrors) {
        this.validationErrors = validationErrors;
        return this;
    }
}