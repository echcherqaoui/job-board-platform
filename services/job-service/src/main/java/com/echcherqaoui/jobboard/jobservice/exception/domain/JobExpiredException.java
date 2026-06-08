package com.echcherqaoui.jobboard.jobservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.exception.enums.JobErrorCode.JOB_EXPIRED;

public class JobExpiredException extends BaseCustomException {

    public JobExpiredException(UUID jobId) {
        super(JOB_EXPIRED, jobId);
    }
}
