package com.echcherqaoui.jobboard.jobservice.grpc.client;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCompanyProfileClientTest {

    private CompanyProfileClient companyProfileClient;
    private ResilientCompanyProfileClient resilientClient;

    @BeforeEach
    void setUp() {
        companyProfileClient = mock(CompanyProfileClient.class);
        resilientClient = new ResilientCompanyProfileClient(companyProfileClient);
    }

    @Test
    void fetchCompanyProfileTolerantly_returnsResult_onHappyPath() {
        CompanySummary summary = CompanySummary.newBuilder().setCompanyName("Acme").build();
        when(companyProfileClient.getCompanyProfileById("user-1")).thenReturn(Optional.of(summary));

        Optional<CompanySummary> result = resilientClient.fetchCompanyProfileTolerantly("user-1");

        assertThat(result).contains(summary);
    }

    @Test
    void fetchCompanyProfileTolerantly_returnsEmpty_whenUnavailable() {
        DownstreamDependencyException ex = new DownstreamDependencyException(
              "user-service", Status.Code.UNAVAILABLE, "connection refused"
        );
        when(companyProfileClient.getCompanyProfileById("user-1")).thenThrow(ex);

        Optional<CompanySummary> result = resilientClient.fetchCompanyProfileTolerantly("user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchCompanyProfileTolerantly_returnsEmpty_whenDeadlineExceeded() {
        DownstreamDependencyException ex = new DownstreamDependencyException(
              "user-service", Status.Code.DEADLINE_EXCEEDED, "timeout"
        );
        when(companyProfileClient.getCompanyProfileById("user-1")).thenThrow(ex);

        Optional<CompanySummary> result = resilientClient.fetchCompanyProfileTolerantly("user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchCompanyProfileTolerantly_rethrows_forNonDegradableErrorCodes() {
        DownstreamDependencyException ex = new DownstreamDependencyException(
              "user-service", Status.Code.PERMISSION_DENIED, "forbidden"
        );
        when(companyProfileClient.getCompanyProfileById("user-1")).thenThrow(ex);

        assertThatThrownBy(() -> resilientClient.fetchCompanyProfileTolerantly("user-1"))
              .isInstanceOf(DownstreamDependencyException.class);
    }
}