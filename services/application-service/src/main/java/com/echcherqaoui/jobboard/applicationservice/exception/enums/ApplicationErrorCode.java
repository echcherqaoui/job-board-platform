package com.echcherqaoui.jobboard.applicationservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationErrorCode implements IErrorCode {
    JOB_NOT_ACCEPTING_APPLICATIONS("BAD_REQUEST_400", "Job %s is not accepting applications", 400),

    NOT_AUTHORIZED_TO_VIEW("FORBIDDEN_403", "You are not authorized to view this application", 403),
    NOT_AUTHORIZED_TO_UPDATE("FORBIDDEN_403", "You are not authorized to update this application", 403),

    JOB_NOT_FOUND_OR_UNAVAILABLE("NOT_FOUND_404", "Job %s not found or unavailable", 404),
    APPLICATION_NOT_FOUND("NOT_FOUND_404", "Application not found: %s", 404),
    JOB_NOT_FOUND("JOB_404", "Job not found: %s", 404),
    APPLICANT_NOT_EXISTS("APPLICANT_404", "Profile not found for user: %s"  , 404),

    ALREADY_APPLIED("ALREADY_APPLIED_409", "You have already applied for job: %s", 409),
    APPLICATION_CONFLICT("OPTIMISTIC_LOCK_409", "Application was modified by another request. Please retry", 409),
    INVALID_STATUS_TRANSITION("INVALID_TRANSITION_409", "Cannot transition application from %s to %s", 409),

    DATA_FETCH_FAILURE("INTERNAL_SERVER_ERROR_500", "Failed to fetch required data: %s", 500);

    private final String code;
    private final String message;
    private final int httpStatus;
}