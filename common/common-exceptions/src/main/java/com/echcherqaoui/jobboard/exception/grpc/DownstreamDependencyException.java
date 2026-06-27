package com.echcherqaoui.jobboard.exception.grpc;

import io.grpc.Status;
import lombok.Getter;
import org.springframework.lang.NonNull;

@Getter
public class DownstreamDependencyException extends RuntimeException {
    private final Status.Code grpcCode;
    private final String serviceName;
    private final String clientMessage;

    public DownstreamDependencyException(String serviceName,
                                         Status.Code grpcCode,
                                         String details) {
        super(String.format("Dependency [%s] failed (%s): %s", serviceName, grpcCode, details));
        this.serviceName = serviceName;
        this.grpcCode = grpcCode;
        this.clientMessage = resolveClientMessage(grpcCode);
    }

    @NonNull
    private static String resolveClientMessage(@NonNull Status.Code code) {
        return switch (code) {
            case NOT_FOUND -> "The requested resource could not be found.";
            case INVALID_ARGUMENT, FAILED_PRECONDITION -> "The request was invalid.";
            case ALREADY_EXISTS -> "The resource already exists.";
            case UNAUTHENTICATED -> "Authentication is required.";
            case PERMISSION_DENIED -> "You do not have permission to perform this action.";
            case DEADLINE_EXCEEDED -> "The request timed out. Please try again.";
            case UNAVAILABLE -> "A required service is temporarily unavailable.";
            case RESOURCE_EXHAUSTED -> "Too many requests. Please try again later.";
            default -> "An unexpected error occurred. Please try again later.";
        };
    }
}