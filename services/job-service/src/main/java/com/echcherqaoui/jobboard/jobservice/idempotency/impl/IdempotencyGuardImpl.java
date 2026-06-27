package com.echcherqaoui.jobboard.jobservice.idempotency.impl;

import com.echcherqaoui.jobboard.jobservice.idempotency.IdempotencyGuard;
import com.echcherqaoui.jobboard.jobservice.model.ProcessedEvent;
import com.echcherqaoui.jobboard.jobservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyGuardImpl implements IdempotencyGuard {
    
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Returns true if this eventId has already been processed.
     */
    @Override
    @Transactional
    public boolean isProcessed(String eventId) {
        // Check first. This does not poison the transaction.
        if (processedEventRepository.existsById(eventId))
            return true;

        processedEventRepository.save(new ProcessedEvent().setEventId(eventId));
        return false;
    }
}