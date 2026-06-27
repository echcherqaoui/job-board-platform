package com.echcherqaoui.jobboard.userservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements IErrorCode {
    CV_EMPTY("CV_EMPTY_400", "Uploaded CV file is empty", 400),
    CV_INVALID_TYPE("CV_TYPE_400", "Only PDF files are accepted (received: %s)", 400),
    CV_FILE_TOO_LARGE("CV_LARGE_400", "CV file exceeds maximum size of %s", 400),
    ONBOARDING_ALREADY_COMPLETED("COMPANY_400", "Onboarding already completed", 400),

    JOB_SEEKER_NOT_EXISTS("PROFILE_404", "Job seeker profile not found for user: %s"  , 404),
    RECRUITER_NOT_EXISTS("RECRUITER_404", "Recruiter profile not found for user: %s"  , 404),
    CV_NOT_FOUND("CV_404", "No CV found for user: %s", 404),

    CV_SIZE_EXCEEDED("CV_SIZE_413", "File size exceeds maximum allowed limit", 413),

    CV_UPLOAD_FAILED("CV_UPLOAD_502", "Failed to upload CV", 502),
    CV_DELETE_FAILED("CV_DELETE_502", "Failed to delete CV", 502);

    private final String code;
    private final String message;
    private final int httpStatus;
}