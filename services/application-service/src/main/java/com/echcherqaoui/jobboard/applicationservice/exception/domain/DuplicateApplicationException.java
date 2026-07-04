package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.ALREADY_APPLIED;

public class DuplicateApplicationException extends BaseCustomException {

    public DuplicateApplicationException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public DuplicateApplicationException(Object... args) {
        super(ALREADY_APPLIED, args);
    }
}
