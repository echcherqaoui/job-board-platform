package com.echcherqaoui.jobboard.jobservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class UnauthorizedJobAccessException extends BaseCustomException {

    public UnauthorizedJobAccessException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

}
