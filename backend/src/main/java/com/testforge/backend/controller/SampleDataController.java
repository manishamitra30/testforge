package com.testforge.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.testforge.backend.service.SampleDataService;
import com.testforge.backend.service.SampleDataService.SampleProject;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sample-project")
@RequiredArgsConstructor
public class SampleDataController {

    private final SampleDataService sampleDataService;

    /** Creates the sample project for the signed-in user (201), or returns the existing one (200). */
    @PostMapping
    public ResponseEntity<SampleProject> create() {
        SampleProject result = sampleDataService.createSampleProject();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}