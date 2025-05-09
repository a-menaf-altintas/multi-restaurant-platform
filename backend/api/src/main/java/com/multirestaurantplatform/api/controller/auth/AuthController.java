package com.multirestaurantplatform.api.controller.auth;

import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.service.JwtService;
import com.multirestaurantplatform.security.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth") // Base path for authentication-related endpoints
@RequiredArgsConstructor // Lombok: Creates constructor for all final fields
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuthenticationManager authenticationManager; // For authenticating users
    private final JwtService jwtService;                     // For generating JWT tokens

    // In AuthController.java
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        // Remove the try-catch block for RuntimeException
        User registeredUser = userService.registerUser(registerRequest); // Let exceptions propagate

        // Consider returning a different DTO instead of the full User entity to avoid exposing too much.
        // For now, a success message or a simplified representation is fine.
        LOGGER.info("User registered successfully: {}", registeredUser.getUsername());

        // For consistency, you might want to consider returning a JSON response even for success.
        // Example:
        // Map<String, String> responseBody = Map.of("message", "User registered successfully with username: " + registeredUser.getUsername());
        // return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully with username: " + registeredUser.getUsername());
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Attempt to authenticate the user using Spring Security's AuthenticationManager
            // This will use your UserDetailsServiceImpl to load the user and check credentials
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            // If authentication is successful, set the authentication in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // The principal is now an instance of UserDetails (as returned by your UserDetailsServiceImpl)
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Generate JWT token using our JwtService
            String jwt = jwtService.generateToken(userDetails);

            LOGGER.info("User authenticated successfully: {}", userDetails.getUsername());
            // Return the JWT in the response
            return ResponseEntity.ok(new JwtAuthenticationResponse(jwt));

        } catch (BadCredentialsException e) {
            LOGGER.warn("Authentication failed for user {}: Invalid credentials", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Invalid username or password");
        } catch (Exception e) {
            LOGGER.error("Authentication error for user {}: {}", loginRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: An internal server error occurred during authentication.");
        }
    }
}
