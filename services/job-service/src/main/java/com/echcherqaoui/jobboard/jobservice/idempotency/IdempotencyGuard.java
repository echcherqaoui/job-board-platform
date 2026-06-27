package com.echcherqaoui.jobboard.jobservice.idempotency;

public interface IdempotencyGuard {
    boolean isProcessed(String eventId);
}
