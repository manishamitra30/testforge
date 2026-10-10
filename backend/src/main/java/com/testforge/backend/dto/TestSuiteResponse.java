package com.testforge.backend.dto;

public record TestSuiteResponse(
        Long id,
        Long projectId,
        String name,
        String description) {}