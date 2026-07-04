package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.JOB_NOT_FOUND;

public class JobNotFoundException extends BaseCustomException {

    public JobNotFoundException(Object... args) {
        super(JOB_NOT_FOUND, args);
    }
}
