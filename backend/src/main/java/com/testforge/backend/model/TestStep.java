package com.testforge.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_steps")
@Getter
@Setter
@NoArgsConstructor
public class TestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;
}