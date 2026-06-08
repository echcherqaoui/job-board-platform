package com.echcherqaoui.jobboard.jobservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobErrorCode implements IErrorCode {
    COMPANY_DOES_NOT_OWN_JOB("JOB_403", "Company %s does not own job %s", 403),
    COMPANY_PROFILE_NOT_FOUND("COMPANY_404", "Company profile for recruiter ID %s could not be found via local cache or remote service.", 404),
    JOB_NOT_FOUND("JOB_404", "Job not found: %s", 404),
    JOB_EXPIRED("JOB_410", "Job has expired. Update the expiry date before publishing: %s", 410);
    private final String code;
    private final String message;
    private final int httpStatus;
}