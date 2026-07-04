package com.echcherqaoui.jobboard.applicationservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.APPLICANT_NOT_EXISTS;

public class ApplicantProfileNotFoundException extends BaseCustomException {

    public ApplicantProfileNotFoundException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ApplicantProfileNotFoundException(Object... args) {
        super(APPLICANT_NOT_EXISTS, args);
    }
}
