package com.echcherqaoui.jobboard.notificationservice.config;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@Component
@GrpcGlobalClientInterceptor
@RequiredArgsConstructor
@Slf4j
public class M2mGrpcBearerTokenInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTH_HEADER =
          Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

    private final OAuth2AuthorizedClientManager authorizedClientManager;


    /**
     * Intercepts outgoing gRPC calls to fetch and inject the M2M Bearer token into gRPC metadata.
     */
    @Override
    public <I, O> ClientCall<I, O> interceptCall(MethodDescriptor<I, O> method,
                                                 CallOptions callOptions,
                                                 Channel next) {

        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
              .withClientRegistrationId("notification-m2m")
              .principal("notification-service")
              .build();

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);

        if (client == null) {
            log.error("Failed to obtain M2M OAuth2 token for registration 'notification-m2m'");
            throw new IllegalStateException("Failed to obtain M2M OAuth2 token");
        }

        String token = client.getAccessToken().getTokenValue();

        log.debug("Successfully injected M2M Bearer token for gRPC method: {}", method.getFullMethodName());

        return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<O> responseListener, Metadata headers) {
                headers.put(AUTH_HEADER, "Bearer " + token);
                super.start(responseListener, headers);
            }
        };
    }

}