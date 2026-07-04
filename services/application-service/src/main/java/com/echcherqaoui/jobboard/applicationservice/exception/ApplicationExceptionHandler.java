package com.echcherqaoui.jobboard.applicationservice.exception;

import com.echcherqaoui.jobboard.exception.response.ErrorResponse;
import com.echcherqaoui.jobboard.exception.util.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.APPLICATION_CONFLICT;
import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.INTERNAL_SERVER_ERROR;

@RestControllerAdvice
@Slf4j
public class ApplicationExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                              WebRequest request) {
        ErrorResponse response = new ErrorResponse(
              APPLICATION_CONFLICT.getCode(),
              APPLICATION_CONFLICT.getMessage(),
              ExceptionUtils.sanitizePath(request)
        );
        return ResponseEntity.status(APPLICATION_CONFLICT.getHttpStatus()).body(response);
    }

    // Searches logs by that ID to find the exact stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                       WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [ID: {}] on [{}]: {}",
              errorId,
              request.getDescription(false),
              ex.getMessage(),
              ex
        );

        ErrorResponse response = new ErrorResponse(
              INTERNAL_SERVER_ERROR.getCode(),
              INTERNAL_SERVER_ERROR.formatMessage(errorId),
              ExceptionUtils.sanitizePath(request)
        );

        return ResponseEntity.status(INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }


}
