package com.echcherqaoui.jobboard.jobservice.grpc.client;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.CompanyProfileNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyProfileClientTest {

    private CompanyProfileServiceGrpc.CompanyProfileServiceBlockingStub companyStub;
    private CompanyProfileClient client;

    @BeforeEach
    void setUp() throws Exception {
        companyStub = mock(CompanyProfileServiceGrpc.CompanyProfileServiceBlockingStub.class);
        when(companyStub.withDeadlineAfter(any())).thenReturn(companyStub);

        client = new CompanyProfileClient();
        Field field = CompanyProfileClient.class.getDeclaredField("companyStub");
        field.setAccessible(true);
        field.set(client, companyStub);
    }

    @Test
    void getCompanyProfileById_returnsCompany_whenFoundAndPresent() {
        String profileId = UUID.randomUUID().toString();
        CompanySummary summary = CompanySummary.newBuilder().setCompanyName("Acme").build();
        GetCompanyProfileResponse response = GetCompanyProfileResponse.newBuilder()
              .setCompany(summary)
              .build();

        when(companyStub.getCompanyProfile(any())).thenReturn(response);

        Optional<CompanySummary> result = client.getCompanyProfileById(profileId);

        assertThat(result).contains(summary);
    }

    @Test
    void getCompanyProfileById_returnsEmpty_whenResponseHasNoCompany() {
        String profileId = UUID.randomUUID().toString();
        GetCompanyProfileResponse response = GetCompanyProfileResponse.newBuilder().build();

        when(companyStub.getCompanyProfile(any())).thenReturn(response);

        Optional<CompanySummary> result = client.getCompanyProfileById(profileId);

        assertThat(result).isEmpty();
    }

    @Test
    void getCompanyProfileById_throwsCompanyProfileNotFoundException_onNotFoundStatus() {
        String profileId = UUID.randomUUID().toString();
        StatusRuntimeException notFound = new StatusRuntimeException(Status.NOT_FOUND);

        when(companyStub.getCompanyProfile(any())).thenThrow(notFound);

        assertThatThrownBy(() -> client.getCompanyProfileById(profileId))
              .isInstanceOf(CompanyProfileNotFoundException.class);
    }

    @Test
    void getCompanyProfileById_throwsIllegalArgumentException_beforeNotFoundHandling_whenProfileIdMalformed() {
        StatusRuntimeException notFound = new StatusRuntimeException(Status.NOT_FOUND);
        when(companyStub.getCompanyProfile(any())).thenThrow(notFound);

        assertThatThrownBy(() -> client.getCompanyProfileById("not-a-uuid"))
              .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompanyProfileById_throwsDownstreamDependencyException_onOtherGrpcErrors() {
        String profileId = UUID.randomUUID().toString();
        StatusRuntimeException unavailable = new StatusRuntimeException(
              Status.UNAVAILABLE.withDescription("connection refused")
        );

        when(companyStub.getCompanyProfile(any())).thenThrow(unavailable);

        assertThatThrownBy(() -> client.getCompanyProfileById(profileId))
              .isInstanceOf(DownstreamDependencyException.class);
    }
}