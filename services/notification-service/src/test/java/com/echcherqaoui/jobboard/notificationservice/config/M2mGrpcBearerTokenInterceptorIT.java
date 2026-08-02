package com.echcherqaoui.jobboard.notificationservice.config;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static io.grpc.CallOptions.DEFAULT;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class M2mGrpcBearerTokenInterceptorIT extends AbstractIntegrationTest {

    private static final Metadata.Key<String> AUTH_HEADER =
          Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

    @Autowired
    private M2mGrpcBearerTokenInterceptor interceptor;

    @MockitoBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    private Server inProcessServer;
    private Channel inProcessChannel;
    private final AtomicReference<Metadata> capturedHeaders = new AtomicReference<>();

    private final MethodDescriptor<String, String> dummyMethod = MethodDescriptor.<String, String>newBuilder()
          .setType(MethodDescriptor.MethodType.UNARY)
          .setFullMethodName("test.TestService/TestMethod")
          .setRequestMarshaller(new StringMarshaller())
          .setResponseMarshaller(new StringMarshaller())
          .build();

    @BeforeEach
    void setupServerAndChannel() throws Exception {
        capturedHeaders.set(null);
        String serverName = InProcessServerBuilder.generateName();

        ServerInterceptor capturingInterceptor = new ServerInterceptor() {
            @Override
            public <I, O> ServerCall.Listener<I> interceptCall(
                  ServerCall<I, O> call,
                  Metadata headers,
                  ServerCallHandler<I, O> next) {
                capturedHeaders.set(headers);
                return next.startCall(call, headers);
            }
        };

        inProcessServer = InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(ServerInterceptors.intercept(new DummyServiceImpl(), capturingInterceptor))
              .build()
              .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
              .directExecutor()
              .build();
    }

    @AfterEach
    void shutdownServer() {
        if (inProcessServer != null) {
            inProcessServer.shutdownNow();
        }
    }

    @Nested
    class TokenInjection {

        @Test
        void interceptCall_WhenTokenAuthorized_ShouldInjectBearerHeader() {
            OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  "test-m2m-token-xyz",
                  Instant.now(),
                  Instant.now().plusSeconds(3600)
            );
            when(authorizedClient.getAccessToken()).thenReturn(accessToken);
            when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient);

            ClientCall<String, String> call = interceptor.interceptCall(dummyMethod, DEFAULT, inProcessChannel);
            ClientCalls.blockingUnaryCall(call, "request-payload");

            Metadata headers = capturedHeaders.get();
            assertThat(headers).isNotNull();
            assertThat(headers.get(AUTH_HEADER)).isEqualTo("Bearer test-m2m-token-xyz");
        }

        @Test
        void interceptCall_WhenClientAuthorizationFails_ShouldThrowIllegalStateException() {
            when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> interceptor.interceptCall(dummyMethod, DEFAULT, inProcessChannel))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessage("Failed to obtain M2M OAuth2 token");

            assertThat(capturedHeaders.get()).isNull();
        }
    }

    private static class DummyServiceImpl implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            MethodDescriptor<String, String> dummyMethod = MethodDescriptor.<String, String>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("test.TestService/TestMethod")
                  .setRequestMarshaller(new StringMarshaller())
                  .setResponseMarshaller(new StringMarshaller())
                  .build();

            return ServerServiceDefinition.builder("test.TestService")
                  .addMethod(dummyMethod, ServerCalls.asyncUnaryCall(
                        (request, responseObserver) -> {
                            responseObserver.onNext("response-payload");
                            responseObserver.onCompleted();
                        }))
                  .build();
        }
    }

    private static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes());
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}