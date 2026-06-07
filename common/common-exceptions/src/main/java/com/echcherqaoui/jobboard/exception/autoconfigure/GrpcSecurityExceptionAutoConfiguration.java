package com.echcherqaoui.jobboard.exception.autoconfigure;

import com.echcherqaoui.jobboard.exception.handler.GrpcSecurityExceptionHandler;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;

@AutoConfiguration
@ConditionalOnClass({GrpcAdvice.class, AccessDeniedException.class})
public class GrpcSecurityExceptionAutoConfiguration {

    @Bean
    public GrpcSecurityExceptionHandler grpcSecurityExceptionHandler() {
        return new GrpcSecurityExceptionHandler();
    }
}