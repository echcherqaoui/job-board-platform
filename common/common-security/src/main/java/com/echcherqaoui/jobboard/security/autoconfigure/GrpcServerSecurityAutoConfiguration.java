package com.echcherqaoui.jobboard.security.autoconfigure;

import com.echcherqaoui.jobboard.security.jwt.JwtAuthConverter;
import net.devh.boot.grpc.server.security.authentication.BearerAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.check.AccessPredicate;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.check.ManualGrpcSecurityMetadataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

@AutoConfiguration
@ConditionalOnClass({GrpcAuthenticationReader.class, JwtDecoder.class})
public class GrpcServerSecurityAutoConfiguration {

    @Bean
    public GrpcAuthenticationReader grpcAuthenticationReader() {
        return new BearerAuthenticationReader(BearerTokenAuthenticationToken::new);
    }

    @Bean("grpcAuthenticationManager")
    public AuthenticationManager grpcAuthenticationManager(JwtDecoder jwtDecoder,
                                                           JwtAuthConverter jwtAuthConverter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthConverter);
        return new ProviderManager(provider);
    }

    @Bean
    @ConditionalOnMissingBean(GrpcSecurityMetadataSource.class)
    public GrpcSecurityMetadataSource grpcSecurityMetadataSource() {
        ManualGrpcSecurityMetadataSource source = new ManualGrpcSecurityMetadataSource();
        source.setDefault(AccessPredicate.permitAll());
        return source;
    }
}