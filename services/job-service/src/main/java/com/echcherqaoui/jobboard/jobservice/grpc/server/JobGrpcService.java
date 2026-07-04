package com.echcherqaoui.jobboard.jobservice.grpc.server;

import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesRequest;
import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesResponse;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryRequest;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryResponse;
import com.echcherqaoui.jobboard.job.grpc.JobServiceGrpc;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.jobservice.mapper.JobGrpcMapper;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class JobGrpcService extends JobServiceGrpc.JobServiceImplBase {
    private final JobService jobService;
    private final JobGrpcMapper grpcMapper;
    private final CompanyProfileService companyProfileService;

    @Override
    @PreAuthorize("hasAnyRole('RECRUITER', 'CANDIDATE')")
    public void getJobSummary(@NonNull GetJobSummaryRequest request,
                              @NonNull StreamObserver<GetJobSummaryResponse> responseObserver) {
        log.info("Received gRPC request to fetch job summary for ID: {}", request.getJobId());

        UUID jobId = UUID.fromString(request.getJobId());

        JobSummaryProjection job = jobService.findJobProjectionById(jobId);

        String companyName = companyProfileService.getCompanyName(job.getRecruiterId());

        GetJobSummaryResponse response = GetJobSummaryResponse.newBuilder()
              .setJob(grpcMapper.toGrpcSummary(job, companyName))
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully returned job summary for ID: {}", jobId);
    }

    @Override
    @PreAuthorize("hasAnyRole('RECRUITER', 'CANDIDATE')")
    public void batchGetJobSummaries(@NonNull BatchGetJobSummariesRequest request,
                                     @NonNull StreamObserver<BatchGetJobSummariesResponse> responseObserver) {
        log.info("Received gRPC batch request to fetch job summaries. Incoming count: {}", request.getJobIdsCount());

        Set<UUID> jobIds = request.getJobIdsList().stream()
              .map(id -> {
                  try {
                      return UUID.fromString(id);
                  } catch (IllegalArgumentException e) {
                      log.warn("Malformed UUID skipped in batch: {}", id);
                      return null;
                  }
              }).filter(Objects::nonNull)
              .collect(Collectors.toSet());

        log.debug("Fetching summaries for {} valid job IDs from service layer", jobIds.size());

        List<JobSummaryProjection> jobs = jobService.getJobsSummaries(jobIds);

        Set<UUID> recruiterIds = jobs.stream()
              .map(JobSummaryProjection::getRecruiterId)
              .collect(Collectors.toSet());

        Map<UUID, String> companyNamesByRecruiterId = companyProfileService.getCompanyNames(recruiterIds);

        List<JobSummary> summaries = jobs.stream()
              .map(job -> grpcMapper.toGrpcSummary(job, companyNamesByRecruiterId.get(job.getRecruiterId())))
              .toList();

        BatchGetJobSummariesResponse response = BatchGetJobSummariesResponse.newBuilder()
              .addAllJobs(summaries)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully completed batch request. Returned {} job summaries", summaries.size());
    }
}
