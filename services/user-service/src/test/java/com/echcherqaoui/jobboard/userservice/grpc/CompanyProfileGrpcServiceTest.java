package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailResponse;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CompanyProfileGrpcServiceTest {

    private RecruiterProfileService recruiterProfileService;
    private CompanyProfileGrpcService grpcService;

    @BeforeEach
    void setUp() {
        recruiterProfileService = mock(RecruiterProfileService.class);
        grpcService = new CompanyProfileGrpcService(recruiterProfileService);
    }

    private RecruiterProfile buildProfile(String companyName, String logoUrl) {
        return new RecruiterProfile()
              .setId(UUID.randomUUID())
              .setCompanyName(companyName)
              .setCompanyLogoUrl(logoUrl);
    }

    @Test
    void getCompanyProfile_happyPath_returnsCompanySummary() {
        UUID profileId = UUID.randomUUID();
        RecruiterProfile profile = buildProfile("Acme", "http://logo.png");
        when(recruiterProfileService.getProfileEntityById(profileId)).thenReturn(profile);

        GetCompanyProfileRequest request = GetCompanyProfileRequest.newBuilder()
              .setProfileId(profileId.toString())
              .build();
        StreamObserver<GetCompanyProfileResponse> observer = mock(StreamObserver.class);

        grpcService.getCompanyProfile(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetCompanyProfileResponse.class);
        verify(observer).onNext(captor.capture());
        CompanySummary summary = captor.getValue().getCompany();
        assertThat(summary.getCompanyName()).isEqualTo("Acme");
        assertThat(summary.getLogoUrl()).isEqualTo("http://logo.png");
        verify(observer).onCompleted();
    }

    @Test
    void getCompanyProfile_nullCompanyFields_defaultToEmptyString() {
        UUID profileId = UUID.randomUUID();
        RecruiterProfile profile = buildProfile(null, null);
        when(recruiterProfileService.getProfileEntityById(profileId)).thenReturn(profile);

        GetCompanyProfileRequest request = GetCompanyProfileRequest.newBuilder()
              .setProfileId(profileId.toString())
              .build();
        StreamObserver<GetCompanyProfileResponse> observer = mock(StreamObserver.class);

        grpcService.getCompanyProfile(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetCompanyProfileResponse.class);
        verify(observer).onNext(captor.capture());
        CompanySummary summary = captor.getValue().getCompany();
        assertThat(summary.getCompanyName()).isEmpty();
        assertThat(summary.getLogoUrl()).isEmpty();
    }

    @Test
    void getRecruiterEmail_happyPath_returnsEmail() {
        UUID profileId = UUID.randomUUID();
        when(recruiterProfileService.getProfileEmailById(profileId)).thenReturn("recruiter@x.com");

        GetRecruiterEmailRequest request = GetRecruiterEmailRequest.newBuilder()
              .setProfileId(profileId.toString())
              .build();
        StreamObserver<GetRecruiterEmailResponse> observer = mock(StreamObserver.class);

        grpcService.getRecruiterEmail(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetRecruiterEmailResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("recruiter@x.com");
        verify(observer).onCompleted();
    }
}