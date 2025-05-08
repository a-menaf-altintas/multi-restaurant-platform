package com.multirestaurantplatform.security.config;

// Note: UserDetailsServiceImpl is not explicitly imported here,
// Spring Security finds it automatically because it implements UserDetailsService and is a @Service

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // For disabling CSRF
import org.springframework.security.config.http.SessionCreationPolicy; // For stateless sessions
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// Import for JWT filter will be needed later:
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration // Indicates this class contains Spring bean definitions
@EnableWebSecurity // Enables Spring Security's web security support
@RequiredArgsConstructor // Lombok: Creates constructor for final fields (if any later)
public class SecurityConfig {

    // Define a Bean for the PasswordEncoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is a strong, widely-used password hashing algorithm
        return new BCryptPasswordEncoder();
    }

    // Define a Bean for the AuthenticationManager
    // This is needed for processes like handling login requests
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // Define the main SecurityFilterChain bean which configures how HTTP requests are handled
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF protection: Common practice for stateless REST APIs
                // where the client doesn't typically use sessions/cookies for auth.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure Session Management to STATELESS: Essential for JWT/token-based auth.
                // Spring Security won't create or use HTTP sessions.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Configure Authorization Rules for HTTP requests
                .authorizeHttpRequests(auth -> auth
                        // Allow unauthenticated access to Swagger UI paths
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        // Allow unauthenticated access to the health check endpoint
                        .requestMatchers("/api/v1/health").permitAll()

                        // ---- VERY IMPORTANT ----
                        // **TEMPORARILY ALLOW ALL OTHER REQUESTS**
                        // This makes development/testing easier initially.
                        // We MUST replace this with specific rules later (e.g., .requestMatchers("/api/orders/**").authenticated())
                        .anyRequest().permitAll()
                        // ---- END TEMPORARY RULE ----
                );

        // 4. TODO: Add JWT Authentication Filter
        // Once we implement JWT, we'll add our custom filter here like this:
        // .addFilterBefore(jwtAuthenticationFilterBean(), UsernamePasswordAuthenticationFilter.class);

        // Build and return the configured HttpSecurity object
        return http.build();
    }

    // 5. TODO: Define the Bean for your JWT Authentication Filter later
    // @Bean
    // public JwtAuthenticationFilter jwtAuthenticationFilterBean() {
    //     // return new JwtAuthenticationFilter(...dependencies...);
    // }
}