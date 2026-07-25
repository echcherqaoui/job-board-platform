package com.echcherqaoui.jobboard.notificationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;


public class EmailNotFoundException extends BaseCustomException {
    public EmailNotFoundException(IErrorCode errorCode, String recruiterId) {
        super(errorCode, recruiterId);
    }
}