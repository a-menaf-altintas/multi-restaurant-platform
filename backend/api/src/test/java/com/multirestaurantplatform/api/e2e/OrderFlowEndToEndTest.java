package com.multirestaurantplatform.api.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.Role;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test that simulates the entire order flow:
 * 1. Register admin, restaurant admin, and customer users
 * 2. Create a restaurant
 * 3. Add restaurant admin to the restaurant
 * 4. Create menus for the restaurant
 * 5. Customer adds items to cart
 * 6. Customer updates cart
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")  // This is key - it loads application-test.properties
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFlowEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Static variables to share data between test methods
    private static String adminToken;
    private static String restaurantAdminToken;
    private static String customerToken;
    private static Long restaurantId;
    private static Long menuId;
    private static Long menuItemId = 101L; // This will be mocked by the MenuServiceClient stub
    private static String customerId = "customer"; // Using username as the customer ID

    @Test
    @Order(1)
    void registerUsers() throws Exception {
        // Register platform admin
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(Role.ADMIN);
        RegisterRequest adminRequest = new RegisterRequest();
        adminRequest.setUsername("admin");
        adminRequest.setPassword("password123");
        adminRequest.setEmail("admin@example.com");
        adminRequest.setRoles(adminRoles);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isCreated());

        // Register restaurant admin
        Set<Role> restaurantAdminRoles = new HashSet<>();
        restaurantAdminRoles.add(Role.RESTAURANT_ADMIN);
        RegisterRequest restaurantAdminRequest = new RegisterRequest();
        restaurantAdminRequest.setUsername("restaurantadmin");
        restaurantAdminRequest.setPassword("password123");
        restaurantAdminRequest.setEmail("restaurantadmin@example.com");
        restaurantAdminRequest.setRoles(restaurantAdminRoles);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restaurantAdminRequest)))
                .andExpect(status().isCreated());

        // Register customer
        Set<Role> customerRoles = new HashSet<>();
        customerRoles.add(Role.CUSTOMER);
        RegisterRequest customerRequest = new RegisterRequest();
        customerRequest.setUsername("customer");
        customerRequest.setPassword("password123");
        customerRequest.setEmail("customer@example.com");
        customerRequest.setRoles(customerRoles);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated());

        // Login as admin to get token
        adminToken = loginAndGetToken("admin", "password123");
        assertNotNull(adminToken, "Admin token should not be null");

        // Login as restaurant admin to get token
        restaurantAdminToken = loginAndGetToken("restaurantadmin", "password123");
        assertNotNull(restaurantAdminToken, "Restaurant admin token should not be null");

        // Login as customer to get token
        customerToken = loginAndGetToken("customer", "password123");
        assertNotNull(customerToken, "Customer token should not be null");
    }

    @Test
    @Order(2)
    void createRestaurant() throws Exception {
        // Admin creates a restaurant
        CreateRestaurantRequestDto createRestaurantDto = new CreateRestaurantRequestDto(
                "Test E2E Restaurant",
                "Restaurant for end-to-end testing",
                "123 Test Street",
                "555-1234",
                "e2e.restaurant@example.com"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/restaurants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRestaurantDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test E2E Restaurant"))
                .andReturn();

        RestaurantResponseDto restaurantResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                RestaurantResponseDto.class);

        restaurantId = restaurantResponse.getId();
        assertNotNull(restaurantId, "Restaurant ID should not be null");
    }

    @Test
    @Order(3)
    void assignRestaurantAdmin() throws Exception {
        // Admin assigns restaurant admin to the restaurant
        UpdateRestaurantRequestDto updateRestaurantDto = new UpdateRestaurantRequestDto();
        // Get the restaurant admin user ID - for simplicity we hardcode it to 2L
        // In a real scenario, you would look up the user ID from the database
        Long restaurantAdminId = 2L; // Assuming the second registered user (after admin) is the restaurant admin
        Set<Long> adminIds = new HashSet<>();
        adminIds.add(restaurantAdminId);
        updateRestaurantDto.setAdminUserIds(adminIds);

        mockMvc.perform(put("/api/v1/restaurants/" + restaurantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRestaurantDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId));
    }

    @Test
    @Order(4)
    void createMenu() throws Exception {
        // Restaurant admin creates a menu
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto(
                "E2E Test Menu",
                "Menu for end-to-end testing",
                restaurantId
        );

        MvcResult result = mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + restaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("E2E Test Menu"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andReturn();

        MenuResponseDto menuResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                MenuResponseDto.class);

        menuId = menuResponse.getId();
        assertNotNull(menuId, "Menu ID should not be null");

        // Verify the menu exists and is associated with the restaurant
        mockMvc.perform(get("/api/v1/menus/by-restaurant/" + restaurantId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(menuId))
                .andExpect(jsonPath("$[0].restaurantId").value(restaurantId));
    }

    @Test
    @Order(5)
    void addItemToCart() throws Exception {
        // Customer adds an item to their cart
        // Note: In a real test, you would need to create menu items first
        // Here we're using the stub MenuServiceClient which will return a fake item with ID 101
        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(
                restaurantId,
                menuItemId,
                2
        );

        MvcResult result = mockMvc.perform(post("/api/v1/users/{userId}/cart/items", customerId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customerId))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.items[0].menuItemId").value(menuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andReturn();

        CartResponse cartResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CartResponse.class);

        assertNotNull(cartResponse, "Cart response should not be null");
        assertEquals(1, cartResponse.getItems().size(), "Cart should have 1 item");
        assertEquals(menuItemId, cartResponse.getItems().get(0).getMenuItemId(), "Cart item should have the correct menu item ID");
    }

    @Test
    @Order(6)
    void updateCartItem() throws Exception {
        // Customer updates an item in their cart
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);

        MvcResult result = mockMvc.perform(put("/api/v1/users/{userId}/cart/items/{menuItemId}", customerId, menuItemId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customerId))
                .andExpect(jsonPath("$.items[0].menuItemId").value(menuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andReturn();

        CartResponse cartResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CartResponse.class);

        assertNotNull(cartResponse, "Cart response should not be null");
        assertEquals(1, cartResponse.getItems().size(), "Cart should have 1 item");
        assertEquals(5, cartResponse.getItems().get(0).getQuantity(), "Cart item should have updated quantity");
    }

    @Test
    @Order(7)
    void removeCartItem() throws Exception {
        // Customer removes an item from their cart
        mockMvc.perform(delete("/api/v1/users/{userId}/cart/items/{menuItemId}", customerId, menuItemId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customerId))
                .andExpect(jsonPath("$.items").isEmpty());

        // Verify cart is empty
        mockMvc.perform(get("/api/v1/users/{userId}/cart", customerId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // Helper method to login a user and get their JWT token
    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JwtAuthenticationResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                JwtAuthenticationResponse.class);

        return response.getAccessToken();
    }
}