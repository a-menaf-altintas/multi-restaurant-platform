package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException; // Import
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.User;

public interface UserService {
    /**
     * Registers a new user based on the provided request data.
     * Handles password encoding and checks for existing username/email.
     *
     * @param registerRequest The user registration data.
     * @return The newly created User entity.
     * @throws com.multirestaurantplatform.common.exception.ConflictException if username or email already exists.
     */
    User registerUser(RegisterRequest registerRequest);

    /**
     * Finds a user by their username.
     *
     * @param username The username to search for.
     * @return The found User entity.
     * @throws ResourceNotFoundException if no user is found with the given username.
     */
    User findUserByUsername(String username);

    /**
     * Finds a user by their ID.
     *
     * @param id The ID of the user to search for.
     * @return The found User entity.
     * @throws ResourceNotFoundException if no user is found with the given ID.
     */
    User findUserById(Long id);

    /**
     * Finds a user by their email address.
     *
     * @param email The email address to search for.
     * @return The found User entity.
     * @throws ResourceNotFoundException if no user is found with the given email.
     */
    User findUserByEmail(String email);

    // Add other methods later, e.g.:
    // User updateUserProfile(Long userId, UpdateProfileRequest request);
}