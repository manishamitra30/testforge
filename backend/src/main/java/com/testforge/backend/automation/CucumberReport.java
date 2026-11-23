package com.testforge.backend.automation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class CucumberReport {

    private CucumberReport() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(String name, List<Tag> tags, List<Element> elements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(String type, String name, List<Tag> tags,
                          List<Step> before, List<Step> steps, List<Step> after) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String name, Result result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String status, Long duration, @JsonProperty("error_message") String errorMessage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(String name) {
    }
}