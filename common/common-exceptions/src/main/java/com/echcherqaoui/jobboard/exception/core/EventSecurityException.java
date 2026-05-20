package com.echcherqaoui.jobboard.exception.core;


import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.INVALID_SIGNATURE;

public class EventSecurityException extends BaseCustomException {
    public EventSecurityException(IErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
    
    public EventSecurityException(String eventId) {
        super(INVALID_SIGNATURE, eventId);
    }
}