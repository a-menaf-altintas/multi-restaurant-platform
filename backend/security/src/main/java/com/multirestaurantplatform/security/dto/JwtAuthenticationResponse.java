package com.multirestaurantplatform.security.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor; // Added for Jackson deserialization

/**
 * Represents the response payload containing the JWT access token.
 */
@Getter
@Setter
@NoArgsConstructor // Provides a no-argument constructor, essential for Jackson deserialization
public class JwtAuthenticationResponse {

    private String accessToken;
    private String tokenType = "Bearer"; // Default token type

    /**
     * Constructs a new JwtAuthenticationResponse with the given access token.
     * The tokenType will default to "Bearer".
     *
     * @param accessToken The JWT access token.
     */
    public JwtAuthenticationResponse(String accessToken) {
        this.accessToken = accessToken;
        // tokenType will retain its default "Bearer" or be set by Jackson's setter if present in JSON
    }

    // Optional: If you need a constructor that sets all fields,
    // you could add @AllArgsConstructor from Lombok, or define it manually:
    //
    // import lombok.AllArgsConstructor;
    // @AllArgsConstructor
    //
    // public JwtAuthenticationResponse(String accessToken, String tokenType) {
    //     this.accessToken = accessToken;
    //     this.tokenType = tokenType;
    // }

    // --- Commented out optional fields for user details ---
    // private String username;
    // private java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;
    //
    // public JwtAuthenticationResponse(String accessToken, String username, java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
    //     this.accessToken = accessToken;
    //     this.username = username;
    //     this.authorities = authorities;
    // }
}
