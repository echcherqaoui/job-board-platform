package com.echcherqaoui.jobboard.jobservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;

import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.exception.enums.JobErrorCode.JOB_NOT_FOUND;

public class JobNotFoundException extends BaseCustomException {

    public JobNotFoundException(UUID jobId) {
        super(JOB_NOT_FOUND, jobId);
    }
}
