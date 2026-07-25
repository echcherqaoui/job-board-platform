package com.echcherqaoui.jobboard.notificationservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements IErrorCode {
    EMAIL_NOT_FOUND("EMAIL_404", "Email could not be found.", 404);

    private final String code;
    private final String message;
    private final int httpStatus;
}