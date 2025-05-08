package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.User; // Assuming User is in model package

public interface UserService {
    /**
     * Registers a new user based on the provided request data.
     * Handles password encoding and checks for existing username/email.
     *
     * @param registerRequest The user registration data.
     * @return The newly created User entity.
     * @throws RuntimeException // Define more specific exceptions later (e.g., UserAlreadyExistsException)
     */
    User registerUser(RegisterRequest registerRequest);

    // Add other methods later, e.g.:
    // Optional<User> findByUsername(String username);
    // User updateUserProfile(Long userId, UpdateProfileRequest request);
}