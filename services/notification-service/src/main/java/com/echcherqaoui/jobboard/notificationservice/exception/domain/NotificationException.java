package com.echcherqaoui.jobboard.notificationservice.exception.domain;


import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;


public class NotificationException extends BaseCustomException {
    public NotificationException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}