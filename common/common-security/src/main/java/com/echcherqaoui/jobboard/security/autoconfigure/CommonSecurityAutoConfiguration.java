package com.echcherqaoui.jobboard.security.autoconfigure;

import com.echcherqaoui.jobboard.security.jwt.JwtAuthConverter;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.security.service.impl.HmacSignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
    public JwtContextHolder jwtContextHolder() {
        return new JwtContextHolder();
    }


    @Bean
    @ConditionalOnClass(JwtAuthenticationToken.class)
    public JwtAuthConverter jwtAuthConverter(@Value("${app.security.jwt.roles-claim:roles}") String rolesClaim) {
        return new JwtAuthConverter(rolesClaim);
    }
}