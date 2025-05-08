package com.multirestaurantplatform.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health") // Base path for health check related endpoints
public class HealthCheckController {

    @GetMapping
    public Map<String, String> checkHealth() {
        return Collections.singletonMap("status", "UP");
    }
}