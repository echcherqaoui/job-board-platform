package com.echcherqaoui.jobboard.exception.core;

import lombok.Getter;

@Getter
public abstract class BaseCustomException extends RuntimeException {

    private final transient IErrorCode errorCode;
    private final transient Object[] args;

    protected BaseCustomException(IErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }
}