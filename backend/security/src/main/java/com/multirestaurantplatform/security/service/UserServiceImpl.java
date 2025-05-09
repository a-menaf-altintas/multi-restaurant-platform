package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException; // Import
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // Lombok: Creates constructor injecting final fields
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Inject the PasswordEncoder bean

    @Override
    @Transactional // Use transaction for operations involving database writes/reads
    public User registerUser(RegisterRequest request) {
        // 1. Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Error: Username '" + request.getUsername() + "' is already taken!");
        }

        // 2. Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Error: Email '" + request.getEmail() + "' is already in use!");
        }

        // 3. Create new user's account
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(request.getRoles());

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true) // Good practice for read operations
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Override
    @Transactional(readOnly = true) // Good practice for read operations
    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true) // Good practice for read operations
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}