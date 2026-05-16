package com.echcherqaoui.jobboard.authservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements IErrorCode {
    PASSWORD_MISMATCH("PASSWORD_400", "Passwords do not match", 400),
    EMAIL_ALREADY_EXISTS("EMAIL_409", "User with email %s already exists", 409),
    USERNAME_ALREADY_EXISTS("USERNAME_409", "User with username %s already exists", 409);

    private final String code;
    private final String message;
    private final int httpStatus;
}