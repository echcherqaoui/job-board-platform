package com.echcherqaoui.jobboard.userservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class CvStorageException extends BaseCustomException {

    public CvStorageException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}