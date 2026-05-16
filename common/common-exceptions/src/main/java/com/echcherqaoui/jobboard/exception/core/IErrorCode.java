package com.echcherqaoui.jobboard.exception.core;

public interface IErrorCode {

    String getCode();
    String getMessage();
    int getHttpStatus();

    /**
     * Default implementation using String.format.
     */
    default String formatMessage(Object... args) {
        return args.length > 0
                ? String.format(getMessage(), args)
                : getMessage();
    }
}