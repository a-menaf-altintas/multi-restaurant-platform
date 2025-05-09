package com.multirestaurantplatform.security.filter;

import com.multirestaurantplatform.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // Ensure this import is present
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.token-prefix}")
    private String tokenPrefix; // Example: "Bearer"

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        LOGGER.debug("JwtAuthenticationFilter: Processing request for URI: {}", request.getRequestURI());
        LOGGER.debug("JwtAuthenticationFilter: Injected tokenPrefix: [{}]", tokenPrefix); // Log the injected prefix

        try {
            final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            LOGGER.debug("JwtAuthenticationFilter: Authorization Header: [{}]", authHeader);

            if (!StringUtils.hasText(tokenPrefix)) {
                LOGGER.error("JwtAuthenticationFilter: tokenPrefix is not configured or empty! Check 'app.jwt.token-prefix' in properties.");
                filterChain.doFilter(request, response);
                return;
            }

            final String expectedPrefixWithSpace = tokenPrefix + " ";

            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(expectedPrefixWithSpace)) {
                LOGGER.debug("JwtAuthenticationFilter: JWT Token does not begin with Bearer string or is missing. Header: [{}], Expected Prefix: [{}]", authHeader, expectedPrefixWithSpace);
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(expectedPrefixWithSpace.length());
            LOGGER.debug("JwtAuthenticationFilter: Extracted JWT: [{}]", jwt);

            final String username = jwtService.extractUsername(jwt);
            LOGGER.debug("JwtAuthenticationFilter: Username extracted from JWT: [{}]", username);

            if (StringUtils.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
                LOGGER.debug("JwtAuthenticationFilter: Username [{}] extracted, SecurityContext is null. Attempting to load UserDetails.", username);
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (userDetails != null) {
                    LOGGER.debug("JwtAuthenticationFilter: UserDetails loaded for username: [{}], Authorities: {}", userDetails.getUsername(), userDetails.getAuthorities());
                    boolean isTokenValid = jwtService.isTokenValid(jwt, userDetails);
                    LOGGER.debug("JwtAuthenticationFilter: Is token valid for username [{}]: {}", username, isTokenValid);

                    if (isTokenValid) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        LOGGER.info("JwtAuthenticationFilter: Successfully authenticated user [{}] and set SecurityContext.", username);
                    } else {
                        LOGGER.warn("JwtAuthenticationFilter: JWT token validation failed for user: {}", username);
                    }
                } else {
                    LOGGER.warn("JwtAuthenticationFilter: UserDetails not found for username extracted from token: {}", username);
                }
            } else {
                LOGGER.debug("JwtAuthenticationFilter: Username not extracted from JWT or SecurityContext already contains authentication. Username: [{}], Auth: {}", username, SecurityContextHolder.getContext().getAuthentication());
            }
        } catch (Exception e) {
            LOGGER.error("JwtAuthenticationFilter: Cannot set user authentication. Error: {}", e.getMessage(), e);
            // SecurityContextHolder.clearContext(); // Consider if necessary
        }

        filterChain.doFilter(request, response);
    }
}
