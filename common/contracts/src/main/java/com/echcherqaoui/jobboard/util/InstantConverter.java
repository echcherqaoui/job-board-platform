package com.echcherqaoui.jobboard.util;

import com.google.protobuf.Timestamp;

import javax.annotation.Nonnull;
import java.time.Instant;

public final class InstantConverter {

    private InstantConverter() {}

    public static Instant toInstant(@Nonnull Timestamp timestamp) {
        return Instant.ofEpochSecond(
              timestamp.getSeconds(),
              timestamp.getNanos()
        );
    }

    @Nonnull
    public static Timestamp toTimestamp(@Nonnull Instant instant) {
        return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .setNanos(instant.getNano())
              .build();
    }
}