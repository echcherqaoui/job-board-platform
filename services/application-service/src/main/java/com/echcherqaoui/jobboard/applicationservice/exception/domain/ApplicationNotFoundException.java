package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.APPLICATION_NOT_FOUND;

public class ApplicationNotFoundException extends BaseCustomException {

    public ApplicationNotFoundException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ApplicationNotFoundException(Object... args) {
        super(APPLICATION_NOT_FOUND, args);
    }
}
