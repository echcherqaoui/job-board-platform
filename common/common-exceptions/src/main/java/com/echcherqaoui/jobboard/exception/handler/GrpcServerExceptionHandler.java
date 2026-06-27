package com.echcherqaoui.jobboard.exception.handler;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.springframework.lang.NonNull;

import static io.grpc.Status.ALREADY_EXISTS;
import static io.grpc.Status.INTERNAL;
import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.NOT_FOUND;
import static io.grpc.Status.PERMISSION_DENIED;
import static io.grpc.Status.RESOURCE_EXHAUSTED;
import static io.grpc.Status.UNAUTHENTICATED;

@GrpcAdvice
public class GrpcServerExceptionHandler {

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleInvalidArgument(@NonNull IllegalArgumentException ex) {
        return INVALID_ARGUMENT.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(BaseCustomException.class)
    public Status handleCustom(@NonNull BaseCustomException ex) {
        return switch (ex.getErrorCode().getHttpStatus()) {
            case 400 -> INVALID_ARGUMENT.withDescription(ex.getMessage());
            case 401 -> UNAUTHENTICATED.withDescription(ex.getMessage());
            case 403 -> PERMISSION_DENIED.withDescription(ex.getMessage());
            case 404 -> NOT_FOUND.withDescription(ex.getMessage());
            case 409, 422 -> ALREADY_EXISTS.withDescription(ex.getMessage());
            case 429 -> RESOURCE_EXHAUSTED.withDescription(ex.getMessage());
            default -> INTERNAL.withDescription(ex.getMessage());
        };
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleGeneric(Exception ex) {
        return INTERNAL.withDescription("Internal server error");
    }
}