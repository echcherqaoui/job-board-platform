package com.echcherqaoui.jobboard.security.autoconfigure;

import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtContextHolder.class)
    public JwtContextHolder jwtContextHolder() {
        return new JwtContextHolder();
    }
}