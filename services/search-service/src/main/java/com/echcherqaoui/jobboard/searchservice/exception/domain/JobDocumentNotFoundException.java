package com.echcherqaoui.jobboard.searchservice.exception.domain;

import com.echcherqaoui.jobboard.exception.core.BaseCustomException;
import com.echcherqaoui.jobboard.exception.core.IErrorCode;

import static com.echcherqaoui.jobboard.searchservice.exception.enums.SearchErrorCode.INDEX_NOT_FOUND;

public class JobDocumentNotFoundException extends BaseCustomException {

    public JobDocumentNotFoundException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public JobDocumentNotFoundException(Object... args) {
        super(INDEX_NOT_FOUND, args);
    }
}
