package com.echcherqaoui.jobboard.exception.autoconfigure;

import com.echcherqaoui.jobboard.exception.handler.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonExceptionsAutoConfiguration {
    
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}