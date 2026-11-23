package com.testforge.backend.automation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.automation")
public record AutomationProperties(
        @DefaultValue("../automation-suite") String suiteDir,
        @DefaultValue("") String workDir,
        @DefaultValue("10") int timeoutMinutes,
        @DefaultValue("true") boolean headless,
        @DefaultValue("1000") int maxLogLines) {
}