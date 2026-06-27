package com.echcherqaoui.jobboard.userservice.exception.domain;


import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

import java.util.UUID;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.JOB_SEEKER_NOT_EXISTS;

public class ProfileNotFoundException extends BaseCustomException {
    public ProfileNotFoundException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ProfileNotFoundException(UUID profileId) {
        super(JOB_SEEKER_NOT_EXISTS, profileId);
    }
}