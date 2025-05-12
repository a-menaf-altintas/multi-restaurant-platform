// File: backend/api/src/test/java/com/multirestaurantplatform/api/e2e/OrderFlowEndToEndTest.java
package com.multirestaurantplatform.api.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.OrderResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.dto.UserResponseDto;
import com.multirestaurantplatform.security.model.Role;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test that simulates the entire order flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OrderFlowEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Static variables to share data between test methods
    private static String adminToken;
    private static String restaurantAdminToken;
    private static String customerToken;
    private static Long restaurantAdminUserId;
    private static Long restaurantId;
    private static Long menuId;
    private static final Long PICKUP_MENU_ITEM_ID = 101L;
    private static final Long DELIVERY_MENU_ITEM_ID = 102L;

    private static final String ADMIN_USERNAME = "e2eAdmin_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String RESTAURANT_ADMIN_USERNAME = "e2eResAdmin_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String CUSTOMER_USERNAME = "e2eCustomer_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String USER_PASSWORD = "Password123!";

    private static Long pickupOrderId;
    private static Long deliveryOrderId;


    @Test
    @Order(1)
    void step1_registerUsersAndLogin() throws Exception {
        System.out.println("Step 1: Registering Users and Logging In...");
        registerUserHelper(ADMIN_USERNAME, ADMIN_USERNAME + "@example.com", USER_PASSWORD, Set.of(Role.ADMIN));
        adminToken = loginAndGetTokenHelper(ADMIN_USERNAME, USER_PASSWORD);
        assertNotNull(adminToken, "Admin token should not be null");

        registerUserHelper(RESTAURANT_ADMIN_USERNAME, RESTAURANT_ADMIN_USERNAME + "@example.com", USER_PASSWORD, Set.of(Role.RESTAURANT_ADMIN));
        restaurantAdminToken = loginAndGetTokenHelper(RESTAURANT_ADMIN_USERNAME, USER_PASSWORD);
        assertNotNull(restaurantAdminToken, "Restaurant admin token should not be null");

        MvcResult userResult = mockMvc.perform(get("/api/v1/users/username/" + RESTAURANT_ADMIN_USERNAME)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        UserResponseDto resAdminUser = objectMapper.readValue(userResult.getResponse().getContentAsString(), UserResponseDto.class);
        restaurantAdminUserId = resAdminUser.getId();
        assertNotNull(restaurantAdminUserId, "Restaurant Admin User ID should not be null");

        registerUserHelper(CUSTOMER_USERNAME, CUSTOMER_USERNAME + "@example.com", USER_PASSWORD, Set.of(Role.CUSTOMER));
        customerToken = loginAndGetTokenHelper(CUSTOMER_USERNAME, USER_PASSWORD);
        assertNotNull(customerToken, "Customer token should not be null");
        System.out.println("Step 1: Completed.");
    }

    @Test
    @Order(2)
    void step2_adminCreatesRestaurant() throws Exception {
        System.out.println("Step 2: Admin Creating Restaurant...");
        assertNotNull(adminToken, "Admin token is required for this step.");
        CreateRestaurantRequestDto createRestaurantDto = new CreateRestaurantRequestDto(
                "E2E Test Restaurant " + UUID.randomUUID().toString().substring(0, 4),
                "Restaurant for end-to-end testing",
                "123 Test Street",
                "555-1234",
                "e2e.restaurant_" + UUID.randomUUID().toString().substring(0, 4) + "@example.com"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/restaurants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRestaurantDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        RestaurantResponseDto restaurantResponse = objectMapper.readValue(result.getResponse().getContentAsString(), RestaurantResponseDto.class);
        restaurantId = restaurantResponse.getId();
        assertNotNull(restaurantId, "Restaurant ID should not be null");
        System.out.println("Step 2: Restaurant created with ID: " + restaurantId);
    }

    @Test
    @Order(3)
    void step3_adminAssignsRestaurantAdmin() throws Exception {
        System.out.println("Step 3: Admin Assigning Restaurant Admin...");
        assertNotNull(adminToken, "Admin token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");
        assertNotNull(restaurantAdminUserId, "Restaurant Admin User ID is required.");

        UpdateRestaurantRequestDto updateRestaurantDto = new UpdateRestaurantRequestDto();
        updateRestaurantDto.setAdminUserIds(Set.of(restaurantAdminUserId));

        mockMvc.perform(put("/api/v1/restaurants/" + restaurantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRestaurantDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId));
        System.out.println("Step 3: Restaurant Admin " + RESTAURANT_ADMIN_USERNAME + " (ID: " + restaurantAdminUserId + ") assigned to restaurant " + restaurantId);
    }

    @Test
    @Order(4)
    void step4_restaurantAdminCreatesMenu() throws Exception {
        System.out.println("Step 4: Restaurant Admin Creating Menu...");
        assertNotNull(restaurantAdminToken, "Restaurant admin token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");

        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto(
                "E2E Main Menu " + UUID.randomUUID().toString().substring(0, 4),
                "Main menu for E2E testing",
                restaurantId
        );

        MvcResult result = mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + restaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        MenuResponseDto menuResponse = objectMapper.readValue(result.getResponse().getContentAsString(), MenuResponseDto.class);
        menuId = menuResponse.getId();
        assertNotNull(menuId, "Menu ID should not be null");
        System.out.println("Step 4: Menu created with ID: " + menuId + " for restaurant " + restaurantId);
    }


    @Test
    @Order(5)
    void step5_customerAddsItemToCart_PickupScenario() throws Exception {
        System.out.println("Step 5: Customer Adding Item for Pickup Scenario...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");

        // Clear cart before this specific scenario to ensure isolation
        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before pickup scenario item add.");


        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, PICKUP_MENU_ITEM_ID, 2);

        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(PICKUP_MENU_ITEM_ID))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        System.out.println("Step 5: Item " + PICKUP_MENU_ITEM_ID + " added to cart for user " + CUSTOMER_USERNAME);
    }

    @Test
    @Order(6)
    void step6_customerPlacesOrder_PickupScenario() throws Exception {
        System.out.println("Step 6: Customer Placing Order for Pickup...");
        assertNotNull(customerToken, "Customer token is required.");

        MvcResult orderResult = mockMvc.perform(post("/api/v1/users/{userId}/orders/place-from-cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value(OrderStatus.PLACED.name()))
                .andReturn();
        OrderResponse placedOrder = objectMapper.readValue(orderResult.getResponse().getContentAsString(), OrderResponse.class);
        pickupOrderId = placedOrder.getId();
        assertNotNull(pickupOrderId, "Pickup Order ID should not be null.");
        assertEquals(restaurantId, placedOrder.getRestaurantId());
        System.out.println("Step 6: Pickup Order placed with ID: " + pickupOrderId);
    }

    @Test
    @Order(7)
    void step7_restaurantAdminProcessesPickupOrder() throws Exception {
        System.out.println("Step 7: Restaurant Admin Processing Pickup Order ID: " + pickupOrderId + "...");
        assertNotNull(restaurantAdminToken, "Restaurant admin token is required.");
        assertNotNull(pickupOrderId, "Pickup Order ID is required.");

        mockMvc.perform(put("/api/v1/orders/" + pickupOrderId + "/confirm")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.CONFIRMED.name()));
        System.out.println("Order " + pickupOrderId + " status: CONFIRMED");

        mockMvc.perform(put("/api/v1/orders/" + pickupOrderId + "/prepare")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.PREPARING.name()));
        System.out.println("Order " + pickupOrderId + " status: PREPARING");

        mockMvc.perform(put("/api/v1/orders/" + pickupOrderId + "/ready-for-pickup")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.READY_FOR_PICKUP.name()));
        System.out.println("Order " + pickupOrderId + " status: READY_FOR_PICKUP");

        mockMvc.perform(put("/api/v1/orders/" + pickupOrderId + "/picked-up")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.DELIVERED.name()));
        System.out.println("Step 7: Order " + pickupOrderId + " status: DELIVERED (Picked Up). Pickup scenario complete.");
    }

    @Test
    @Order(8)
    void step8_customerAddsItemToCart_DeliveryScenario() throws Exception {
        System.out.println("Step 8: Customer Adding Item for Delivery Scenario...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");

        // Explicitly clear cart for this new scenario to avoid interference
        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before delivery scenario item add.");


        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, DELIVERY_MENU_ITEM_ID, 1);

        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(DELIVERY_MENU_ITEM_ID))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items.length()").value(1)); // Ensure only this item is in cart
        System.out.println("Step 8: Item " + DELIVERY_MENU_ITEM_ID + " added to cart for delivery order by user " + CUSTOMER_USERNAME);
    }

    @Test
    @Order(9)
    void step9_customerPlacesOrder_DeliveryScenario() throws Exception {
        System.out.println("Step 9: Customer Placing Order for Delivery...");
        assertNotNull(customerToken, "Customer token is required.");

        MvcResult orderResult = mockMvc.perform(post("/api/v1/users/{userId}/orders/place-from-cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value(OrderStatus.PLACED.name()))
                .andReturn();
        OrderResponse placedOrder = objectMapper.readValue(orderResult.getResponse().getContentAsString(), OrderResponse.class);
        deliveryOrderId = placedOrder.getId();
        assertNotNull(deliveryOrderId, "Delivery Order ID should not be null.");
        assertEquals(restaurantId, placedOrder.getRestaurantId());
        System.out.println("Step 9: Delivery Order placed with ID: " + deliveryOrderId);
    }

    @Test
    @Order(10)
    void step10_restaurantAdminProcessesDeliveryOrder() throws Exception {
        System.out.println("Step 10: Restaurant Admin Processing Delivery Order ID: " + deliveryOrderId + "...");
        assertNotNull(restaurantAdminToken, "Restaurant admin token is required.");
        assertNotNull(deliveryOrderId, "Delivery Order ID is required.");

        mockMvc.perform(put("/api/v1/orders/" + deliveryOrderId + "/confirm")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.CONFIRMED.name()));
        System.out.println("Order " + deliveryOrderId + " status: CONFIRMED");

        mockMvc.perform(put("/api/v1/orders/" + deliveryOrderId + "/prepare")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.PREPARING.name()));
        System.out.println("Order " + deliveryOrderId + " status: PREPARING");

        mockMvc.perform(put("/api/v1/orders/" + deliveryOrderId + "/ready-for-pickup")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.READY_FOR_PICKUP.name()));
        System.out.println("Order " + deliveryOrderId + " status: READY_FOR_PICKUP");

        mockMvc.perform(put("/api/v1/orders/" + deliveryOrderId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.OUT_FOR_DELIVERY.name()));
        System.out.println("Order " + deliveryOrderId + " status: OUT_FOR_DELIVERY");

        mockMvc.perform(put("/api/v1/orders/" + deliveryOrderId + "/delivery-completed")
                        .header("Authorization", "Bearer " + restaurantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.DELIVERED.name()));
        System.out.println("Step 10: Order " + deliveryOrderId + " status: DELIVERED (Delivery Completed). Delivery scenario complete.");
    }


    @Test
    @Order(11)
    void step11_cartManipulation_AddItemAgain() throws Exception {
        System.out.println("Step 11: Cart Manipulation - Adding item again...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");

        // Explicitly clear cart before this specific cart manipulation test
        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before step 11.");

        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, PICKUP_MENU_ITEM_ID, 1);
        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(PICKUP_MENU_ITEM_ID)) // Check specific item
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items.length()").value(1)); // Ensure only this item is in cart
        System.out.println("Step 11: Item added to cart.");
    }


    @Test
    @Order(12)
    void step12_cartManipulation_UpdateCartItem() throws Exception {
        System.out.println("Step 12: Cart Manipulation - Updating cart item...");
        // This test assumes item PICKUP_MENU_ITEM_ID was added in step 11
        assertNotNull(customerToken, "Customer token is required.");

        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);
        mockMvc.perform(put("/api/v1/users/{userId}/cart/items/{menuItemId}", CUSTOMER_USERNAME, PICKUP_MENU_ITEM_ID)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(PICKUP_MENU_ITEM_ID))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        System.out.println("Step 12: Cart item updated.");
    }

    @Test
    @Order(13)
    void step13_cartManipulation_RemoveCartItem() throws Exception {
        System.out.println("Step 13: Cart Manipulation - Removing cart item...");
        // This test assumes item PICKUP_MENU_ITEM_ID (updated in step 12) is in the cart
        assertNotNull(customerToken, "Customer token is required.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart/items/{menuItemId}", CUSTOMER_USERNAME, PICKUP_MENU_ITEM_ID)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()); // After removing the only item, cart should be empty
        System.out.println("Step 13: Cart item removed, cart should be empty.");
    }

    // Helper method to register a user
    private void registerUserHelper(String username, String email, String password, Set<Role> roles) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setEmail(email);
        registerRequest.setPassword(password);
        registerRequest.setRoles(roles);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());
    }

    // Helper method to login a user and get their JWT token
    private String loginAndGetTokenHelper(String username, String password) throws Exception {
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
