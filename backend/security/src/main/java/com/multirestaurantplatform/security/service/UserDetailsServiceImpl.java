package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Ensure transactional context

import java.util.Collection;
import java.util.stream.Collectors;

@Service // Marks this as a Spring service bean
@RequiredArgsConstructor // Lombok: Creates a constructor injecting final fields (UserRepository)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Use read-only transaction for fetching data
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Fetch the user from the database via the repository
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // Convert our application's Role enum to Spring Security's GrantedAuthority
        // We add the "ROLE_" prefix as it's a common Spring Security convention
        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        // Return Spring Security's User object (which implements UserDetails)
        // This includes username, password (hashed), account status flags, and authorities
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(), // Spring Security expects the hashed password from the DB
                true, // enabled - TODO: Add an 'isActive' field to User entity later if needed
                true, // accountNonExpired - TODO: Add logic later if needed
                true, // credentialsNonExpired - TODO: Add logic later if needed
                true, // accountNonLocked - TODO: Add logic later if needed
                authorities);
    }
}