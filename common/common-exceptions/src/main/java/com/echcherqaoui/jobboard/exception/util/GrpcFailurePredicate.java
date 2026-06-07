package com.echcherqaoui.jobboard.exception.util;

import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

public class GrpcFailurePredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable t) {
        if (t instanceof StatusRuntimeException ex) {
            return switch (ex.getStatus().getCode()) {
                case UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED -> true;
                default -> false;
            };
        }
        return false;
    }
}