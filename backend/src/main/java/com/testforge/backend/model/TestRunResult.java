package com.testforge.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_run_results")
@Getter
@Setter
@NoArgsConstructor
public class TestRunResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private TestRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus status = ResultStatus.NOT_RUN;

    @Column(name = "duration_ms")
    private Long durationMs = 0L;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by")
    private User executedBy;
}