package com.echcherqaoui.jobboard.exception.handler;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;


import static io.grpc.Status.PERMISSION_DENIED;

@GrpcAdvice
public class GrpcSecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GrpcSecurityExceptionHandler.class);

    @GrpcExceptionHandler(AccessDeniedException.class)
    public Status handleAccessDenied(@NonNull AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return PERMISSION_DENIED.withDescription(ex.getMessage());
    }
}