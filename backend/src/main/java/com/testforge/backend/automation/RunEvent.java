package com.testforge.backend.automation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunEvent(String type, Long runId, String status, String line, Long offset, String at) {
}