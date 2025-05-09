package com.multirestaurantplatform.security.config; // Or your chosen package for this class

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component // Marks this as a Spring component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    /**
     * This method is invoked when an unauthenticated user attempts to access a secured REST resource.
     * It sends an HTTP 401 Unauthorized response.
     *
     * @param request       that resulted in an <code>AuthenticationException</code>
     * @param response      so that the user agent can begin authentication
     * @param authException that caused the invocation
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        // Log the unauthorized attempt for monitoring/debugging purposes
        LOGGER.error("Unauthorized error: {}. Path: {}", authException.getMessage(), request.getRequestURI());

        // Send an HTTP 401 Unauthorized error back to the client
        // You can customize the response further if needed, e.g., by sending a JSON body
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Error: Unauthorized - " + authException.getMessage());
    }
}
