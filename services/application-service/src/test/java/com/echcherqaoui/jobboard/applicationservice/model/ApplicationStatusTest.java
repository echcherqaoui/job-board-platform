package com.echcherqaoui.jobboard.applicationservice.model;

import org.junit.jupiter.api.Test;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApplicationStatusTest {

    @Test
    void pending_canTransitionTo_reviewedRejectedCanceled_only() {
        assertThat(PENDING.canTransitionTo(REVIEWED)).isTrue();
        assertThat(PENDING.canTransitionTo(REJECTED)).isTrue();
        assertThat(PENDING.canTransitionTo(CANCELED)).isTrue();
        assertThat(PENDING.canTransitionTo(ACCEPTED)).isFalse();
        assertThat(PENDING.canTransitionTo(PENDING)).isFalse();
    }

    @Test
    void reviewed_canTransitionTo_acceptedRejectedCanceled_only() {
        assertThat(REVIEWED.canTransitionTo(ACCEPTED)).isTrue();
        assertThat(REVIEWED.canTransitionTo(REJECTED)).isTrue();
        assertThat(REVIEWED.canTransitionTo(CANCELED)).isTrue();
        assertThat(REVIEWED.canTransitionTo(PENDING)).isFalse();
    }

    @Test
    void accepted_canOnlyTransitionTo_canceled() {
        assertThat(ACCEPTED.canTransitionTo(CANCELED)).isTrue();
        assertThat(ACCEPTED.canTransitionTo(REJECTED)).isFalse();
        assertThat(ACCEPTED.canTransitionTo(REVIEWED)).isFalse();
    }

    @Test
    void rejectedAndCanceled_areTerminal_noTransitionsAllowed() {
        for (ApplicationStatus target : ApplicationStatus.values()) {
            assertThat(REJECTED.canTransitionTo(target)).isFalse();
            assertThat(CANCELED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void everyEnumValue_hasATransitionMapEntry() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThatCode(() -> status.canTransitionTo(PENDING))
                  .doesNotThrowAnyException();
        }
    }
}