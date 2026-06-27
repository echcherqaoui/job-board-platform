package com.echcherqaoui.jobboard.authservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import static com.echcherqaoui.jobboard.authservice.exception.enums.AuthErrorCode.PASSWORD_MISMATCH;

public class PasswordMismatchException extends BaseCustomException {
    public PasswordMismatchException() {
        super(PASSWORD_MISMATCH);
    }
}