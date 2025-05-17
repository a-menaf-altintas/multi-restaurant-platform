package com.multirestaurantplatform.api.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.common.dto.error.ErrorResponse;
import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem; // Ensure this import is present
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
        assertNotNull(errorResponse.timestamp());
    }

    // Test for duplicate email
    @Test
    void registerUser_whenEmailAlreadyExists_shouldReturnConflict() throws Exception {
        // Arrange: Create an initial user
        RegisterRequest initialRequest = new RegisterRequest();
        initialRequest.setUsername("user1_email_test");
        initialRequest.setEmail("existing.email@example.com");
        initialRequest.setPassword("Password123!");
        initialRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Attempt to register with the same email but different username
        RegisterRequest duplicateEmailRequest = new RegisterRequest();
        duplicateEmailRequest.setUsername("user2_email_test");
        duplicateEmailRequest.setEmail("existing.email@example.com"); // Same email
        duplicateEmailRequest.setPassword("Password456!");
        duplicateEmailRequest.setRoles(Set.of(Role.CUSTOMER));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                // Corrected message assertion:
                .andExpect(jsonPath("$.message", containsString("Error: Email 'existing.email@example.com' is already in use!")))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    // Tests for RegisterRequest validation failures
    @Test
    void registerUser_whenUsernameIsBlank_shouldReturnBadRequest() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(""); // Blank username
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("Password123!");
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // Corrected JSON path for array of errors
                .andExpect(jsonPath("$.errors", hasItem(containsString("Username cannot be blank"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("Username must be between 3 and 50 characters"))));
    }

    @Test
    void registerUser_whenEmailIsInvalid_shouldReturnBadRequest() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("validuser");
        registerRequest.setEmail("invalid-email"); // Invalid email format
        registerRequest.setPassword("Password123!");
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // Corrected JSON path for array of errors
                .andExpect(jsonPath("$.errors[0]", containsString("Email should be valid")));
    }

    @Test
    void registerUser_whenPasswordIsTooShort_shouldReturnBadRequest() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("validuser");
        registerRequest.setEmail("valid@example.com");
        registerRequest.setPassword("short"); // Password too short
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // Corrected JSON path for array of errors
                .andExpect(jsonPath("$.errors[0]", containsString("Password must be between 8 and 100 characters")));
    }

    // --- Login Endpoint Tests ---

    @Test
    void loginUser_withValidCredentials_shouldReturnOkAndJwt() throws Exception {
        // Arrange: First, register a user
        String username = "loginuser";
        String password = "PasswordForLogin123!";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setEmail("loginuser@example.com");
        registerRequest.setPassword(password);
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Act: Attempt to log in
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        String responseString = result.getResponse().getContentAsString();
        JwtAuthenticationResponse jwtResponse = objectMapper.readValue(responseString, JwtAuthenticationResponse.class);
        assertNotNull(jwtResponse.getAccessToken());
        assertTrue(jwtResponse.getAccessToken().length() > 10);
    }

    @Test
    void loginUser_withInvalidPassword_shouldReturnUnauthorized() throws Exception {
        // Arrange: Register a user
        String username = "userpassfail";
        String correctPassword = "CorrectPassword123!";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setEmail("userpassfail@example.com");
        registerRequest.setPassword(correctPassword);
        registerRequest.setRoles(Set.of(Role.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Act: Attempt to log in with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword("WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Error: Invalid username or password"));
    }

    @Test
    void loginUser_withNonExistentUsername_shouldReturnUnauthorized() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("nonexistentuser");
        loginRequest.setPassword("AnyPassword123!");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Error: Invalid username or password"));
    }

    @Test
    void loginUser_whenUsernameIsBlank_shouldReturnBadRequest() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(""); // Blank username
        loginRequest.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // Corrected JSON path for array of errors
                .andExpect(jsonPath("$.errors[0]", containsString("Username cannot be blank")));
    }

    @Test
    void loginUser_whenPasswordIsBlank_shouldReturnBadRequest() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword(""); // Blank password

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                // Corrected JSON path for array of errors
                .andExpect(jsonPath("$.errors[0]", containsString("Password cannot be blank")));
    }
}