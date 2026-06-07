package com.echcherqaoui.jobboard.jobservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.exception.enums.JobErrorCode.COMPANY_PROFILE_NOT_FOUND;

public class CompanyProfileNotFoundException extends BaseCustomException {

    public CompanyProfileNotFoundException(UUID recruiterId) {
        super(COMPANY_PROFILE_NOT_FOUND, recruiterId);
    }
}