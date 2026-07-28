package com.echcherqaoui.jobboard.jobservice.grpc.server;

import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesRequest;
import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesResponse;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryRequest;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryResponse;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.jobservice.mapper.JobGrpcMapper;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobGrpcServiceTest {

    private JobService jobService;
    private JobGrpcMapper grpcMapper;
    private CompanyProfileService companyProfileService;
    private JobGrpcService grpcService;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        grpcMapper = mock(JobGrpcMapper.class);
        companyProfileService = mock(CompanyProfileService.class);
        grpcService = new JobGrpcService(jobService, grpcMapper, companyProfileService);
    }

    @SuppressWarnings("unchecked")
    private StreamObserver<GetJobSummaryResponse> mockSingleObserver() {
        return mock(StreamObserver.class);
    }

    @SuppressWarnings("unchecked")
    private StreamObserver<BatchGetJobSummariesResponse> mockBatchObserver() {
        return mock(StreamObserver.class);
    }

    // ---------- getJobSummary ----------

    @Test
    void getJobSummary_buildsResponse_andCompletesStream() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        GetJobSummaryRequest request = GetJobSummaryRequest.newBuilder().setJobId(jobId.toString()).build();
        StreamObserver<GetJobSummaryResponse> observer = mockSingleObserver();

        JobSummaryProjection projection = mock(JobSummaryProjection.class);
        when(projection.getRecruiterId()).thenReturn(recruiterId);
        when(jobService.findJobProjectionById(jobId)).thenReturn(projection);
        when(companyProfileService.getCompanyName(recruiterId)).thenReturn("Acme");

        JobSummary summary = JobSummary.newBuilder().build();
        when(grpcMapper.toGrpcSummary(projection, "Acme")).thenReturn(summary);

        grpcService.getJobSummary(request, observer);

        org.mockito.ArgumentCaptor<GetJobSummaryResponse> captor =
              org.mockito.ArgumentCaptor.forClass(GetJobSummaryResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());

        assertThatResponseWrapsSummary(captor.getValue(), summary);
    }

    private void assertThatResponseWrapsSummary(GetJobSummaryResponse response, JobSummary expected) {
        org.assertj.core.api.Assertions.assertThat(response.getJob()).isEqualTo(expected);
    }

    @Test
    void getJobSummary_looksUpCompanyName_usingProjectionsRecruiterId() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        GetJobSummaryRequest request = GetJobSummaryRequest.newBuilder().setJobId(jobId.toString()).build();

        JobSummaryProjection projection = mock(JobSummaryProjection.class);
        when(projection.getRecruiterId()).thenReturn(recruiterId);
        when(jobService.findJobProjectionById(jobId)).thenReturn(projection);
        when(companyProfileService.getCompanyName(recruiterId)).thenReturn("Acme");
        when(grpcMapper.toGrpcSummary(any(), any())).thenReturn(JobSummary.newBuilder().build());

        grpcService.getJobSummary(request, mockSingleObserver());

        verify(companyProfileService).getCompanyName(recruiterId);
    }

    // ---------- batchGetJobSummaries ----------

    @Test
    void batchGetJobSummaries_skipsMalformedUuids_silently() {
        UUID validId = UUID.randomUUID();
        BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
              .addJobIds(validId.toString())
              .addJobIds("not-a-uuid")
              .build();

        when(jobService.getJobsSummaries(Set.of(validId))).thenReturn(List.of());
        when(companyProfileService.getCompanyNames(anySet())).thenReturn(Map.of());

        grpcService.batchGetJobSummaries(request, mockBatchObserver());

        verify(jobService).getJobsSummaries(Set.of(validId));
    }

    @Test
    void batchGetJobSummaries_buildsResponse_withEnrichedCompanyNames() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
              .addJobIds(jobId.toString())
              .build();

        JobSummaryProjection projection = mock(JobSummaryProjection.class);
        when(projection.getRecruiterId()).thenReturn(recruiterId);

        when(jobService.getJobsSummaries(Set.of(jobId))).thenReturn(List.of(projection));
        when(companyProfileService.getCompanyNames(Set.of(recruiterId)))
              .thenReturn(Map.of(recruiterId, "Acme"));

        JobSummary summary = JobSummary.newBuilder().build();
        when(grpcMapper.toGrpcSummary(projection, "Acme")).thenReturn(summary);

        StreamObserver<BatchGetJobSummariesResponse> observer = mockBatchObserver();
        grpcService.batchGetJobSummaries(request, observer);

        org.mockito.ArgumentCaptor<BatchGetJobSummariesResponse> captor =
              org.mockito.ArgumentCaptor.forClass(BatchGetJobSummariesResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();

        org.assertj.core.api.Assertions.assertThat(captor.getValue().getJobsList()).containsExactly(summary);
    }

    @Test
    void batchGetJobSummaries_passesNullCompanyName_whenRecruiterHasNoMapping() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
              .addJobIds(jobId.toString())
              .build();

        JobSummaryProjection projection = mock(JobSummaryProjection.class);
        when(projection.getRecruiterId()).thenReturn(recruiterId);

        when(jobService.getJobsSummaries(Set.of(jobId))).thenReturn(List.of(projection));
        when(companyProfileService.getCompanyNames(Set.of(recruiterId))).thenReturn(Map.of()); // no mapping

        when(grpcMapper.toGrpcSummary(any(), any())).thenReturn(JobSummary.newBuilder().build());

        grpcService.batchGetJobSummaries(request, mockBatchObserver());

        verify(grpcMapper).toGrpcSummary(projection, null);
    }

    @Test
    void batchGetJobSummaries_handlesEmptyJobIdsList() {
        BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder().build();

        when(jobService.getJobsSummaries(Set.of())).thenReturn(List.of());
        when(companyProfileService.getCompanyNames(Set.of())).thenReturn(Map.of());

        StreamObserver<BatchGetJobSummariesResponse> observer = mockBatchObserver();
        grpcService.batchGetJobSummaries(request, observer);

        org.mockito.ArgumentCaptor<BatchGetJobSummariesResponse> captor =
              org.mockito.ArgumentCaptor.forClass(BatchGetJobSummariesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat_emptyJobsList(captor.getValue());
    }

    private void assertThat_emptyJobsList(BatchGetJobSummariesResponse response) {
        org.assertj.core.api.Assertions.assertThat(response.getJobsList()).isEmpty();
    }
}