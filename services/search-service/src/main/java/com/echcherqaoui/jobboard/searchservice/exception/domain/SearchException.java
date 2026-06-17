package com.echcherqaoui.jobboard.searchservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

public class SearchException extends BaseCustomException {
    public SearchException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}