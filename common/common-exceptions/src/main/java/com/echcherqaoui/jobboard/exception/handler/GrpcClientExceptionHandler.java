package com.echcherqaoui.jobboard.exception.handler;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.exception.response.ErrorResponse;
import com.echcherqaoui.jobboard.exception.util.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10) // let module-specific handlers win first
@Slf4j
public class GrpcClientExceptionHandler {

    @ExceptionHandler(DownstreamDependencyException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamFailure(@NonNull DownstreamDependencyException ex, WebRequest request) {

        HttpStatus status = switch (ex.getGrpcCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, FAILED_PRECONDITION -> HttpStatus.CONFLICT;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        // Prevent alerting noise: Warn on 4xx Client Errors, Error on 5xx Server/Infra Errors
        if (status.is4xxClientError())
            log.warn(
                  "Downstream contract violation [Service: {}] [Status: {}]: {}",
                  ex.getServiceName(),
                  status,
                  ex.getMessage()
            );
        else
            log.error(
                  "Downstream infrastructure failure [Service: {}] [Status: {}]: {}",
                  ex.getServiceName(),
                  status,
                  ex.getMessage()
            );

        return ResponseEntity.status(status).body(
              new ErrorResponse(
                    "DEPENDENCY_FAILURE",
                    ex.getClientMessage(),
                    ExceptionUtils.sanitizePath(request)
              )
        );
    }
}