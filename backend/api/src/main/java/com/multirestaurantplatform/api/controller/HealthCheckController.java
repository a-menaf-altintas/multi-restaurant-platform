package com.multirestaurantplatform.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // For method-level security
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test") // Changed base path for clarity, or keep /health and add new mapping
public class HealthCheckController { // Renaming to TestController might be better if adding more test endpoints

    @GetMapping("/health") // This remains public as per SecurityConfig
    public Map<String, String> checkHealth() {
        return Collections.singletonMap("status", "UP");
    }

    // New SECURED endpoint
    @GetMapping("/secure-data")
    // @PreAuthorize("isAuthenticated()") // Alternative: method-level security if @EnableMethodSecurity is on SecurityConfig
    public ResponseEntity<Map<String, Object>> getSecureData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = "anonymous";

        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            username = userDetails.getUsername();
        } else if (authentication != null) {
            username = authentication.getName();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "This is secured data for authenticated users.");
        data.put("user", username);
        data.put("authorities", authentication != null ? authentication.getAuthorities() : Collections.emptyList());

        return ResponseEntity.ok(data);
    }

    // Example of an admin-only endpoint (requires @EnableMethodSecurity in SecurityConfig)
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')") // Ensure your roles are prefixed with ROLE_ in UserDetails authorities, or use hasAuthority('ADMIN')
    public ResponseEntity<String> getAdminData() {
        return ResponseEntity.ok("This is data only for users with the ADMIN role.");
    }
}
