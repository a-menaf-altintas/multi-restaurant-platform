package com.multirestaurantplatform.api.controller.auth;

import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.User; // Or a specific DTO if you prefer for the response
import com.multirestaurantplatform.security.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth") // Base path for authentication-related endpoints
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            User registeredUser = userService.registerUser(registerRequest);
            // Consider returning a different DTO instead of the full User entity to avoid exposing too much.
            // For now, a success message or a simplified representation is fine.
            // Example: return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully!");
            // Or for a bit more info without exposing sensitive details:
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully with username: " + registeredUser.getUsername());
        } catch (RuntimeException e) { // Catch specific exceptions like UserAlreadyExistsException later
            // It's good practice to log the exception here: log.error("Registration failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // We will add a login endpoint here in the next step (1.2f) after JWT setup.
}