package com.echcherqaoui.jobboard.security.grpc;

import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@Slf4j
@RequiredArgsConstructor
public class JwtClientInterceptor implements ClientInterceptor {

    private final JwtContextHolder jwtContextHolder;

    private static final Metadata.Key<String> AUTH_HEADER =
          Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

    @Override
    public <I, O> ClientCall<I, O> interceptCall(MethodDescriptor<I, O> method,
                                                 CallOptions callOptions,
                                                 Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<O> responseListener, Metadata headers) {
                try {
                    Jwt jwt = jwtContextHolder.getJwt();
                    headers.put(AUTH_HEADER, "Bearer " + jwt.getTokenValue());
                } catch (Exception e) {
                    log.warn("No JWT found, gRPC call sent unauthenticated: {}", e.getMessage());
                }
                super.start(responseListener, headers);
            }
        };
    }
}