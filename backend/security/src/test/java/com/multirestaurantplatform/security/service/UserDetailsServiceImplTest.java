package com.multirestaurantplatform.security.service;

import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("testuser@example.com");
        testUser.setPassword("hashedPassword"); // Represents the stored hashed password
        testUser.setRoles(Collections.emptySet()); // Initialize roles to an empty set
    }

    @Test
    void loadUserByUsername_UserFound_ReturnsUserDetails() {
        // Arrange
        Set<Role> roles = Set.of(Role.CUSTOMER, Role.ADMIN);
        testUser.setRoles(roles);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert
        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals("hashedPassword", userDetails.getPassword()); // Should be the stored hash
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isCredentialsNonExpired());
        assertTrue(userDetails.isAccountNonLocked());

        Set<SimpleGrantedAuthority> expectedAuthorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
        assertEquals(expectedAuthorities.size(), userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().containsAll(expectedAuthorities));

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsUsernameNotFoundException() {
        // Arrange
        String username = "nonexistentuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername(username);
        });
        assertEquals("User not found with username: " + username, exception.getMessage());

        verify(userRepository).findByUsername(username);
    }

    @Test
    void loadUserByUsername_UserFoundWithNoRoles_ReturnsUserDetailsWithNoAuthorities() {
        // Arrange
        testUser.setRoles(Collections.emptySet()); // User has no roles
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert
        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals(0, userDetails.getAuthorities().size()); // Expect no authorities

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void loadUserByUsername_UserFoundWithOneRole_ReturnsUserDetailsWithCorrectAuthority() {
        // Arrange
        Set<Role> roles = Set.of(Role.RESTAURANT_ADMIN);
        testUser.setRoles(roles);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert
        assertNotNull(userDetails);
        assertEquals(1, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_RESTAURANT_ADMIN")));

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void loadUserByUsername_UsernameIsCaseSensitiveInRepositoryMock() {
        // Arrange
        // This test primarily ensures our mock behaves as expected for case sensitivity.
        // The actual service passes the username as is.
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername("TestUser")).thenReturn(Optional.empty());

        // Act & Assert for "testuser"
        assertNotNull(userDetailsService.loadUserByUsername("testuser"));

        // Act & Assert for "TestUser" (expecting not found based on mock)
        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername("TestUser");
        });

        verify(userRepository).findByUsername("testuser");
        verify(userRepository).findByUsername("TestUser");
    }
}