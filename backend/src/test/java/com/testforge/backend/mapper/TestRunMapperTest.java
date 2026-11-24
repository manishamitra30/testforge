package com.testforge.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.testforge.backend.dto.RunSummary;
import com.testforge.backend.model.ResultStatus;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestRunMapperTest {

    private final TestRunMapper mapper = new TestRunMapper();

    @Test
    void anEmptyRunHasAllZerosAndNoDivisionByZero() {
        RunSummary summary = mapper.toSummary(Map.of());

        assertThat(summary).isEqualTo(new RunSummary(0, 0, 0, 0, 0, 0, 0.0));
    }

    @Test
    void notRunResultsDoNotCountAsExecuted() {
        RunSummary summary = mapper.toSummary(Map.of(ResultStatus.NOT_RUN, 4L));

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.notRun()).isEqualTo(4);
        assertThat(summary.passRate()).isZero();
    }

    @ParameterizedTest(name = "{0} passed, {1} failed, {2} not run -> {3}%")
    @CsvSource({
            "5,1,0,83.3",
            "1,2,0,33.3",
            "2,1,0,66.7",
            "3,0,0,100.0",
            "0,3,0,0.0",
            "5,1,10,83.3"})
    void passRateIsPassedOverExecutedRoundedToOneDecimal(long passed, long failed, long notRun, double expected) {
        Map<ResultStatus, Long> counts = new EnumMap<>(ResultStatus.class);
        counts.put(ResultStatus.PASSED, passed);
        counts.put(ResultStatus.FAILED, failed);
        counts.put(ResultStatus.NOT_RUN, notRun);

        assertThat(mapper.toSummary(counts).passRate()).isEqualTo(expected);
    }

    @Test
    void blockedAndSkippedResultsCountAsExecutedButNotAsPassed() {
        Map<ResultStatus, Long> counts = new EnumMap<>(ResultStatus.class);
        counts.put(ResultStatus.PASSED, 1L);
        counts.put(ResultStatus.BLOCKED, 1L);
        counts.put(ResultStatus.SKIPPED, 2L);

        RunSummary summary = mapper.toSummary(counts);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.passRate()).isEqualTo(25.0);
    }
}