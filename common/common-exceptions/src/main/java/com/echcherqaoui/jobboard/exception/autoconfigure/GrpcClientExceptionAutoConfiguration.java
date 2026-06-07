package com.echcherqaoui.jobboard.exception.autoconfigure;

import com.echcherqaoui.jobboard.exception.handler.GrpcClientExceptionHandler;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(GrpcClient.class)
public class GrpcClientExceptionAutoConfiguration {

    @Bean
    public GrpcClientExceptionHandler grpcClientExceptionHandler() {
        return new GrpcClientExceptionHandler();
    }
}