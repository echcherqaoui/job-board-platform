package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class UnauthorizedAccessException extends BaseCustomException {

    public UnauthorizedAccessException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
