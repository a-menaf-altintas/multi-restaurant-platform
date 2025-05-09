package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.security.dto.UserResponseDto;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users") // Base path for user-related endpoints
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Endpoint to get a user by username
    @GetMapping("/username/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> getUserByUsername(@PathVariable String username) {
        User user = userService.findUserByUsername(username);
        UserResponseDto userResponseDto = mapToUserResponseDto(user);
        return ResponseEntity.ok(userResponseDto);
    }

    // New Endpoint: Get a user by ID
    @GetMapping("/id/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        User user = userService.findUserById(id);
        UserResponseDto userResponseDto = mapToUserResponseDto(user);
        return ResponseEntity.ok(userResponseDto);
    }

    // New Endpoint: Get a user by email
    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email) {
        User user = userService.findUserByEmail(email); // Assuming email path variable should be URL-friendly
        // For emails, sometimes using a request param is preferred
        // e.g., /users/search?email=user@example.com
        // But /email/{email} is also common.
        UserResponseDto userResponseDto = mapToUserResponseDto(user);
        return ResponseEntity.ok(userResponseDto);
    }

    // Helper method to map User entity to UserResponseDto
    private UserResponseDto mapToUserResponseDto(User user) {
        if (user == null) {
            return null; // Should not happen if service throws ResourceNotFoundException
        }
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles()
        );
    }
}