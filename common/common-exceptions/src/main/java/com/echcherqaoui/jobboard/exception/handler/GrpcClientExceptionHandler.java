package com.echcherqaoui.jobboard.exception.handler;

import com.echcherqaoui.jobboard.exception.response.ErrorResponse;
import com.echcherqaoui.jobboard.exception.util.ExceptionUtils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestControllerAdvice
@Order(HIGHEST_PRECEDENCE)
@Slf4j
public class GrpcClientExceptionHandler {

    private static final Set<Status.Code> ERROR_CODES = Set.of(
          Status.Code.UNAVAILABLE,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.INTERNAL
    );

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGrpcException(@NonNull StatusRuntimeException ex, WebRequest request) {
        Status status = ex.getStatus();
        Status.Code grpcCode = status.getCode();

        String errorId = UUID.randomUUID().toString();

        String description = status.getDescription() != null
              ? status.getDescription()
              : ex.getLocalizedMessage();

        // Log internal details for debugging
        if (ERROR_CODES.contains(grpcCode))
            log.error(
                  "gRPC failure [ID: {}] code [{}] on [{}]: {}",
                  errorId,
                  grpcCode,
                  request.getDescription(false),
                  description
            );
        else
            log.warn(
                  "gRPC failure [ID: {}] code [{}] on [{}]: {}",
                  errorId,
                  grpcCode,
                  request.getDescription(false),
                  description
            );


        // Map gRPC status codes to corresponding HTTP status codes
        Map.Entry<HttpStatus, String> mapping = switch (grpcCode) {
            case NOT_FOUND -> Map.entry(NOT_FOUND, "The requested resource was not found — reference: %s");
            case INVALID_ARGUMENT -> Map.entry(BAD_REQUEST, "Invalid request parameters — reference: %s");
            case UNAUTHENTICATED -> Map.entry(UNAUTHORIZED, "Authentication required — reference: %s");
            case PERMISSION_DENIED -> Map.entry(FORBIDDEN, "Access denied — reference: %s");
            case DEADLINE_EXCEEDED -> Map.entry(GATEWAY_TIMEOUT, "The request timed out — reference: %s");
            case UNAVAILABLE -> Map.entry(SERVICE_UNAVAILABLE, "Service temporarily unavailable — reference: %s");
            default -> Map.entry(INTERNAL_SERVER_ERROR, "An internal error occurred — reference: %s");
        };

        return ResponseEntity.status(mapping.getKey()).body(
              new ErrorResponse(
                    mapping.getKey().name(),
                    String.format(mapping.getValue(), errorId),
                    ExceptionUtils.sanitizePath(request)
              )
        );
    }
}