package com.multirestaurantplatform.api.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.api.dto.error.ErrorResponse;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource; // Import this
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.jwt.secret=FZrx/+48fJdLdRjR7xESLZFrEbP/3gEUZhfyH9cG3mRWOGmzxZaEhyaZsSgjGCtUD2tKOuQUoqLXWosZbl9DTg=="
        // You can add other properties here if needed for tests, e.g.:
        // "logging.level.com.multirestaurantplatform=DEBUG"
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerUser_whenValidRequest_shouldReturnCreatedAndSuccessMessage() throws Exception {
        // Arrange
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("newuser");
        registerRequest.setEmail("newuser@example.com");
        registerRequest.setPassword("Password123!");
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("User registered successfully with username: newuser"));

        assertTrue(userRepository.existsByUsername("newuser"));
    }

    @Test
    void registerUser_whenUsernameAlreadyExists_shouldReturnConflict() throws Exception {
        // Arrange: Create an initial user
        RegisterRequest initialRequest = new RegisterRequest();
        initialRequest.setUsername("existinguser");
        initialRequest.setEmail("existing@example.com");
        initialRequest.setPassword("Password123!");
        initialRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Attempt to register with the same username
        RegisterRequest duplicateUsernameRequest = new RegisterRequest();
        duplicateUsernameRequest.setUsername("existinguser");
        duplicateUsernameRequest.setEmail("another@example.com");
        duplicateUsernameRequest.setPassword("Password456!");
        duplicateUsernameRequest.setRoles(Set.of(Role.CUSTOMER));

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateUsernameRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("Error: Username 'existinguser' is already taken!")))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        assertNotNull(errorResponse.timestamp()); // Corrected accessor
    }

    // TODO: Add test for duplicate email
    // TODO: Add tests for validation failures (e.g., blank username, short password) -> should return 400
}