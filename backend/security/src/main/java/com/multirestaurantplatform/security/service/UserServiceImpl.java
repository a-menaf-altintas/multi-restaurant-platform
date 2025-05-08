package com.multirestaurantplatform.security.service;

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
            // TODO: Replace with custom, more specific exception
            throw new RuntimeException("Error: Username is already taken!");
        }

        // 2. Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            // TODO: Replace with custom, more specific exception
            throw new RuntimeException("Error: Email is already in use!");
        }

        // 3. Create new user's account
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // 4. Encode the password before saving!
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(request.getRoles());
        // BaseEntity fields (id, createdAt, updatedAt) will be handled by JPA/Hibernate

        // 5. Save the user to the database
        return userRepository.save(user);
    }
}