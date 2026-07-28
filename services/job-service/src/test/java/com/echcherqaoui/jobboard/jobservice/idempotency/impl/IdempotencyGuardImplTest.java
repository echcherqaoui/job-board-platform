package com.echcherqaoui.jobboard.jobservice.idempotency.impl;

import com.echcherqaoui.jobboard.jobservice.model.ProcessedEvent;
import com.echcherqaoui.jobboard.jobservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardImplTest {

    private ProcessedEventRepository processedEventRepository;
    private IdempotencyGuardImpl guard;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        guard = new IdempotencyGuardImpl(processedEventRepository);
    }

    @Test
    void isProcessed_returnsFalse_andSavesNewRecord_whenEventIdNotSeenBefore() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(false);

        boolean result = guard.isProcessed("evt-1");

        assertThat(result).isFalse();

        org.mockito.ArgumentCaptor<ProcessedEvent> captor = org.mockito.ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
    }

    @Test
    void isProcessed_returnsTrue_andSkipsSave_whenEventIdAlreadyExists() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(true);

        boolean result = guard.isProcessed("evt-1");

        assertThat(result).isTrue();
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void isProcessed_isNotAtomic_documentingRaceConditionRisk() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(false);

        guard.isProcessed("evt-1");

        verify(processedEventRepository).existsById("evt-1");
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}