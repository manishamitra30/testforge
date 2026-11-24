package com.testforge.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DefectStatusTest {

    @ParameterizedTest
    @EnumSource(DefectStatus.class)
    void aDefectCanNeverMoveToItsOwnStatus(DefectStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(DefectStatus.class)
    void everyStatusHasAWayOut(DefectStatus status) {
        assertThat(status.allowedTransitions()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(DefectStatus.class)
    void canTransitionToAgreesWithAllowedTransitions(DefectStatus from) {
        for (DefectStatus to : DefectStatus.values()) {
            assertThat(from.canTransitionTo(to)).isEqualTo(from.allowedTransitions().contains(to));
        }
    }

    @Test
    void aClosedDefectCanOnlyBeReopened() {
        assertThat(DefectStatus.CLOSED.allowedTransitions()).containsExactly(DefectStatus.OPEN);
    }

    @Test
    void aDefectMustBeWorkedBeforeItIsResolved() {
        assertThat(DefectStatus.OPEN.canTransitionTo(DefectStatus.RESOLVED)).isFalse();
        assertThat(DefectStatus.IN_PROGRESS.canTransitionTo(DefectStatus.RESOLVED)).isTrue();
    }

    @Test
    void allowedTransitionsCannotBeModifiedByTheCaller() {
        var allowed = DefectStatus.OPEN.allowedTransitions();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> allowed.add(DefectStatus.RESOLVED));
        assertThat(Arrays.asList(DefectStatus.values())).contains(DefectStatus.OPEN);
    }
}