package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Good practice for read operations
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LOGGER.debug("Attempting to load user by username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    LOGGER.warn("User not found with username: {}", username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });

        LOGGER.info("User found: {}. Stored hashed password: [PROTECTED]", user.getUsername()); // Don't log the actual hash unless for very specific, temporary debugging.
        // For temporary deep debugging, you could log user.getPassword() but remove it immediately after.
        // LOGGER.debug("Hashed password from DB for user {}: {}", username, user.getPassword());


        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> {
                    LOGGER.debug("Mapping role: {} to authority: ROLE_{}", role.name(), role.name());
                    return new SimpleGrantedAuthority("ROLE_" + role.name());
                })
                .collect(Collectors.toSet());

        LOGGER.debug("Authorities for user {}: {}", username, authorities);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(), // This is the stored hashed password
                true, // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities);
    }
}
