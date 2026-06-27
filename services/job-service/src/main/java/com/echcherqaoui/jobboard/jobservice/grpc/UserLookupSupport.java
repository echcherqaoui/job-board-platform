package com.echcherqaoui.jobboard.jobservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLookupSupport {
    private final UserServiceClient userServiceClient;

    public Optional<CompanySummary> fetchCompanyProfileTolerantly(String userId) {
        try {
            return userServiceClient.getCompanyProfileById(userId);
        } catch (DownstreamDependencyException ex) {
            Status.Code code = ex.getGrpcCode();

            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                // Service is down/slow — degrade gracefully.
                log.warn("user-service unavailable for userId={}, degrading: {}", userId, code);
                return Optional.empty();
            }
            throw ex;
        }
    }
}
