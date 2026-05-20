package com.echcherqaoui.jobboard.security.autoconfigure;

import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.security.service.impl.HmacSignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CommonSecurityAutoConfiguration {

    /**
     * Registers HmacSignatureService as the default ISignatureService.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.hmac.enabled", havingValue = "true", matchIfMissing = true)
    public SignatureService signatureService(@Value("${app.security.hmac.secret}") String secret) {
        return new HmacSignatureService(secret);
    }

    @Bean
    @ConditionalOnMissingBean(JwtContextHolder.class)
    public JwtContextHolder jwtContextHolder() {
        return new JwtContextHolder();
    }
}