package com.echcherqaoui.jobboard.jobservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class BadRequestException extends BaseCustomException {

    public BadRequestException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

}