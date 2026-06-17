package com.echcherqaoui.jobboard.searchservice.exception.enums;

import com.echcherqaoui.jobboard.exception.core.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements IErrorCode {
    INDEX_NOT_FOUND("NOT_FOUND_404", "Index does not exist %s", 404),
    NULL_SOURCE("NULL_SOURCE_404", "Null source in Elasticsearch hit", 404),
    QUERY_FAILED("QUERY_500", "Elasticsearch query failed", 500),
    CONNECTION_ERROR("CONNECTION_ERROR_503", "Cannot connect to search cluster", 503);

    private final String code;
    private final String message;
    private final int httpStatus;
}