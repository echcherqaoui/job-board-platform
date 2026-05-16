package com.echcherqaoui.jobboard.exception.util;

import org.springframework.web.context.request.WebRequest;

public final class ExceptionUtils {
    
    private ExceptionUtils() {}

    public static String sanitizePath(WebRequest request) {
        return request.getDescription(false)
              .replace("uri=", "");
    }
}