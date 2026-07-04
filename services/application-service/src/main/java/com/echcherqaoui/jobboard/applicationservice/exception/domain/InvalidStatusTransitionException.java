package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.INVALID_STATUS_TRANSITION;

public class InvalidStatusTransitionException extends BaseCustomException {

    public InvalidStatusTransitionException(Object... args) {
        super(INVALID_STATUS_TRANSITION, args);
    }
}
