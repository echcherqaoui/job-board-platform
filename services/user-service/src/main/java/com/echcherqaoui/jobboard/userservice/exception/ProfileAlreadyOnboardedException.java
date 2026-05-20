package com.echcherqaoui.jobboard.userservice.exception;


import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.ONBOARDING_ALREADY_COMPLETED;

public class ProfileAlreadyOnboardedException extends BaseCustomException {
    public ProfileAlreadyOnboardedException() {
        super(ONBOARDING_ALREADY_COMPLETED);
    }
}