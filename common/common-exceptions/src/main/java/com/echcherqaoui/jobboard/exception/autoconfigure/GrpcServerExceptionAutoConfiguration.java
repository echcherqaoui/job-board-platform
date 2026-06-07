package com.echcherqaoui.jobboard.exception.autoconfigure;

import com.echcherqaoui.jobboard.exception.handler.GrpcServerExceptionHandler;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(GrpcAdvice.class)
public class GrpcServerExceptionAutoConfiguration {

    @Bean
    public GrpcServerExceptionHandler grpcExceptionHandler() {
        return new GrpcServerExceptionHandler();
    }
}