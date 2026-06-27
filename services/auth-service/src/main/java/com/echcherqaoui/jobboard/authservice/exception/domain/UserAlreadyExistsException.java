package com.echcherqaoui.jobboard.authservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class UserAlreadyExistsException extends BaseCustomException {
    public UserAlreadyExistsException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}