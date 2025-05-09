package com.multirestaurantplatform.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtService.class);

    @Value("${app.jwt.secret}")
    private String jwtSecretString;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * HMAC key derived from {@code jwtSecretString}. The type must be {@link SecretKey}
     * so that the new JJWT 0.12 verify/sign methods resolve without a cast.
     */
    private SecretKey signingKey;

    /**
     * Convert the Base64‑encoded secret string into a {@link SecretKey} once the bean is ready.
     */
    @PostConstruct
    public void init() {
        if (jwtSecretString == null || jwtSecretString.trim().isEmpty()) {
            LOGGER.error("JWT secret key is null or empty – check app.jwt.secret");
            throw new IllegalArgumentException("JWT secret key cannot be null or empty");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecretString);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception ex) {
            LOGGER.error("Invalid Base64 value for app.jwt.secret: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid JWT secret key", ex);
        }
    }

    /* ---------------------------------------------------------------------
     *  Public API
     * ------------------------------------------------------------------ */

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Build a JWS with optional extra claims using the JJWT 0.12 fluent API
     * (no deprecated setters).
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims()                   // switch into the Claims builder
                .add(extraClaims)       // merge custom claims
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpirationMs))
                .and()                  // back to the main builder
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            return userDetails.getUsername().equals(extractUsername(token)) && !isTokenExpired(token);
        } catch (io.jsonwebtoken.JwtException ex) {
            LOGGER.warn("JWT validation failed for user {}: {}", userDetails.getUsername(), ex.getMessage());
            return false;
        }
    }

    /* ---------------------------------------------------------------------
     *  Private helpers
     * ------------------------------------------------------------------ */

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parse and verify the supplied compact JWS, returning its {@link Claims} payload.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
