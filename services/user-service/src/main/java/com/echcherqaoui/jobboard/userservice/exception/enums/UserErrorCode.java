package com.echcherqaoui.jobboard.userservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements IErrorCode {
    ONBOARDING_ALREADY_COMPLETED("COMPANY_400", "Onboarding already completed", 400),
    JOB_SEEKER_NOT_EXISTS("PROFILE_404", "Job seeker profile not found for user: %s"  , 404),
    RECRUITER_NOT_EXISTS("RECRUITER_404", "Recruiter profile not found for user: %s"  , 404);

    private final String code;
    private final String message;
    private final int httpStatus;
}