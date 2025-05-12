package com.multirestaurantplatform.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    // Test values
    private final String testSecret = "ZGZnaGRuanNkZmd0cnRld3J0Z2ZkZ2RmcmdlcnRyZGZnZHJnZHJnZA=="; // Base64 encoded random string
    private final long testExpirationMs = 3600000; // 1 hour in milliseconds
    private final String testUsername = "testuser";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecretString", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", testExpirationMs);
        // Manually invoke init since Spring isn't managing this bean in the test
        jwtService.init();
    }

    @Test
    void generateToken_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");

        // Act
        String token = jwtService.generateToken(userDetails);

        // Assert
        assertNotNull(token);
        assertTrue(token.length() > 0);

        // Verify claims
        Claims claims = extractAllClaims(token);
        assertEquals(testUsername, claims.getSubject());
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
        assertTrue(claims.getIssuedAt().getTime() <= System.currentTimeMillis());
    }

    @Test
    void generateToken_WithExtraClaims_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");
        HashMap<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("customClaim", "customValue");

        // Act
        String token = jwtService.generateToken(extraClaims, userDetails);

        // Assert
        assertNotNull(token);

        // Verify claims
        Claims claims = extractAllClaims(token);
        assertEquals(testUsername, claims.getSubject());
        assertEquals("customValue", claims.get("customClaim"));
    }

    @Test
    void extractUsername_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");
        String token = jwtService.generateToken(userDetails);

        // Act
        String extractedUsername = jwtService.extractUsername(token);

        // Assert
        assertEquals(testUsername, extractedUsername);
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");
        String token = jwtService.generateToken(userDetails);

        // Act
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");

        // Set a very short expiration time for testing
        long originalExpiration = (long) ReflectionTestUtils.getField(jwtService, "jwtExpirationMs");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -10000L); // Expired 10 seconds ago

        String expiredToken = jwtService.generateToken(userDetails);

        // Restore original expiration for other tests
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", originalExpiration);

        // Act
        boolean isValid = jwtService.isTokenValid(expiredToken, userDetails);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_DifferentUser_ReturnsFalse() {
        // Arrange
        UserDetails userDetails1 = createUserDetails(testUsername, "ROLE_USER");
        UserDetails userDetails2 = createUserDetails("differentuser", "ROLE_USER");

        String token = jwtService.generateToken(userDetails1);

        // Act
        boolean isValid = jwtService.isTokenValid(token, userDetails2);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_InvalidToken_ReturnsFalse() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTUxNjIzOTAyMn0.ThisSignatureIsInvalid";

        // Act
        boolean isValid = jwtService.isTokenValid(invalidToken, userDetails);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void extractClaim_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(testUsername, "ROLE_USER");
        String token = jwtService.generateToken(userDetails);

        // Act
        Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);

        // Assert
        assertNotNull(issuedAt);
        assertTrue(issuedAt.getTime() <= System.currentTimeMillis());
    }

    // Helper methods

    private UserDetails createUserDetails(String username, String... roles) {
        // Fix: Use collect to get a Collection instead of toList() which returns a List
        Collection<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new User(username, "password", authorities);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(testSecret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}