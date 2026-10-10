package com.testforge.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

@GetMapping("/hello")
public Map<String, String> getHello() {
    return Map.of("message", "TestForge backend is running");
}
}