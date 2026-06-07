package com.echcherqaoui.jobboard.security.autoconfigure;

import com.echcherqaoui.jobboard.security.grpc.JwtClientInterceptor;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ClientInterceptor.class})
public class GrpcClientSecurityAutoConfiguration {
    @Bean
    @GrpcGlobalClientInterceptor
    public JwtClientInterceptor jwtClientInterceptor(JwtContextHolder jwtContextHolder) {
        return new JwtClientInterceptor(jwtContextHolder);
    }
}