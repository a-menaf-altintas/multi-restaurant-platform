// File: backend/api/src/test/java/com/multirestaurantplatform/api/e2e/OrderFlowEndToEndTest.java
package com.multirestaurantplatform.api.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.menu.dto.CreateMenuItemRequestDto;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuItemResponseDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.order.dto.*;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.order.service.client.MenuItemDetailsDto; // Added
import com.multirestaurantplatform.order.service.client.StubMenuServiceClientImpl; // Added
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.dto.UserResponseDto;
import com.multirestaurantplatform.security.model.Role;
import org.junit.jupiter.api.BeforeEach; // Added
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Autowired
    private OrderService orderService;

    @Autowired // Autowire the stub implementation
    private StubMenuServiceClientImpl stubMenuServiceClient;

    // Static variables to share data between test methods
    private static String adminToken;
    private static String restaurantAdminToken;
    private static String customerToken;
    private static Long restaurantAdminUserId;
    private static Long restaurantId;
    private static String restaurantName; // Store restaurant name
    private static Long menuId;

    private static Long dynamicPickupMenuItemId;
    private static String dynamicPickupMenuItemName = "E2E Pickup Burger";
    private static BigDecimal dynamicPickupMenuItemPrice = new BigDecimal("10.99");

    private static Long dynamicDeliveryMenuItemId;
    private static String dynamicDeliveryMenuItemName = "E2E Delivery Pizza";
    private static BigDecimal dynamicDeliveryMenuItemPrice = new BigDecimal("15.50");

    private static Long secondRestaurantId; // For step 14
    private static String secondRestaurantName; // For step 14
    private static Long dynamicSecondRestaurantItemId;
    private static String dynamicSecondRestaurantItemName = "Second Restaurant Item";
    private static BigDecimal dynamicSecondRestaurantItemPrice = new BigDecimal("7.77");


    private static final String ADMIN_USERNAME = "e2eAdmin_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String RESTAURANT_ADMIN_USERNAME = "e2eResAdmin_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String CUSTOMER_USERNAME = "e2eCustomer_" + UUID.randomUUID().toString().substring(0, 6);
    private static final String USER_PASSWORD = "Password123!";

    private static Long pickupOrderId;
    private static Long deliveryOrderId;

    @BeforeEach
    void setUpEachTest() {
        // Clear the stub's mock database before certain tests if necessary,
        // especially if not relying on @DirtiesContext for full reset or if tests might interfere.
        // For this sequential E2E, we'll populate it as we go.
        // If step4b is the first one populating, it's fine.
        // If tests were independent, this would be more critical.
        // stubMenuServiceClient.clearMockDatabase(); // Example: clear before each test
    }


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
        String uniqueRestaurantName = "E2E Test Restaurant " + UUID.randomUUID().toString().substring(0, 4);
        CreateRestaurantRequestDto createRestaurantDto = new CreateRestaurantRequestDto(
                uniqueRestaurantName,
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
        restaurantName = restaurantResponse.getName(); // Store restaurant name
        assertNotNull(restaurantId, "Restaurant ID should not be null");
        assertNotNull(restaurantName, "Restaurant Name should not be null");
        System.out.println("Step 2: Restaurant created with ID: " + restaurantId + ", Name: " + restaurantName);
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
    @Order(5) // Changed order
    void step5_restaurantAdminCreatesMenuItems() throws Exception { // Renamed from step4b
        System.out.println("Step 5: Restaurant Admin Creating Menu Items...");
        assertNotNull(restaurantAdminToken, "Restaurant admin token is required.");
        assertNotNull(menuId, "Menu ID is required for creating menu items.");
        assertNotNull(restaurantId, "Restaurant ID is required for MenuItemDetailsDto.");
        assertNotNull(restaurantName, "Restaurant Name is required for MenuItemDetailsDto.");

        stubMenuServiceClient.clearMockDatabase(); // Clear stub before populating for this test run section

        // Create Pickup Item
        CreateMenuItemRequestDto pickupItemDto = new CreateMenuItemRequestDto(
                dynamicPickupMenuItemName,
                "A tasty burger for pickup.",
                dynamicPickupMenuItemPrice,
                null,
                menuId,
                "Vegetarian option available",
                true
        );
        MvcResult pickupResult = mockMvc.perform(post("/api/v1/menu-items")
                        .header("Authorization", "Bearer " + restaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pickupItemDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        MenuItemResponseDto pickupItemResponse = objectMapper.readValue(pickupResult.getResponse().getContentAsString(), MenuItemResponseDto.class);
        dynamicPickupMenuItemId = pickupItemResponse.getId();
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID should not be null");
        // Add to stub
        stubMenuServiceClient.addMenuItemDetails(new MenuItemDetailsDto(
                dynamicPickupMenuItemId, dynamicPickupMenuItemName, dynamicPickupMenuItemPrice, restaurantId, restaurantName, true));
        System.out.println("Step 5: Pickup MenuItem created with ID: " + dynamicPickupMenuItemId + " and added to stub.");

        // Create Delivery Item
        CreateMenuItemRequestDto deliveryItemDto = new CreateMenuItemRequestDto(
                dynamicDeliveryMenuItemName,
                "A delicious pizza for delivery.",
                dynamicDeliveryMenuItemPrice,
                null,
                menuId,
                "Contains gluten",
                true
        );
        MvcResult deliveryResult = mockMvc.perform(post("/api/v1/menu-items")
                        .header("Authorization", "Bearer " + restaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryItemDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        MenuItemResponseDto deliveryItemResponse = objectMapper.readValue(deliveryResult.getResponse().getContentAsString(), MenuItemResponseDto.class);
        dynamicDeliveryMenuItemId = deliveryItemResponse.getId();
        assertNotNull(dynamicDeliveryMenuItemId, "Dynamic Delivery MenuItem ID should not be null");
        // Add to stub
        stubMenuServiceClient.addMenuItemDetails(new MenuItemDetailsDto(
                dynamicDeliveryMenuItemId, dynamicDeliveryMenuItemName, dynamicDeliveryMenuItemPrice, restaurantId, restaurantName, true));
        System.out.println("Step 5: Delivery MenuItem created with ID: " + dynamicDeliveryMenuItemId + " and added to stub.");
        System.out.println("Step 5: Completed.");
    }


    @Test
    @Order(6) // Changed order
    void step6_customerAddsItemToCart_PickupScenario() throws Exception { // Renamed from step5
        System.out.println("Step 6: Customer Adding Item for Pickup Scenario...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required for this step.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before pickup scenario item add.");

        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, dynamicPickupMenuItemId, 2);

        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicPickupMenuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        System.out.println("Step 6: Item " + dynamicPickupMenuItemId + " added to cart for user " + CUSTOMER_USERNAME);
    }

    @Test
    @Order(7) // Changed order
    @Transactional
    void step7_customerPlacesAndRestaurantProcessesPickupOrder() throws Exception { // Renamed from step6And7
        System.out.println("Step 7: Customer Placing Order for Pickup & Restaurant Processing...");
        assertNotNull(customerToken, "Customer token is required.");

        MvcResult orderResult = mockMvc.perform(post("/api/v1/users/{userId}/orders/place-from-cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING_PAYMENT.name()))
                .andReturn();
        OrderResponse placedOrder = objectMapper.readValue(orderResult.getResponse().getContentAsString(), OrderResponse.class);
        pickupOrderId = placedOrder.getId();
        assertNotNull(pickupOrderId, "Pickup Order ID should not be null.");
        assertEquals(restaurantId, placedOrder.getRestaurantId());
        System.out.println("Sub-step 7.1: Pickup Order placed with ID: " + pickupOrderId + ", Status: " + placedOrder.getStatus());

        if (pickupOrderId != null) {
            System.out.println("Sub-step 7.2: Simulating payment success for pickup Order ID: " + pickupOrderId);
            orderService.processPaymentSuccess(pickupOrderId, "test_pi_pickup_" + UUID.randomUUID().toString());
            System.out.println("Pickup Order ID: " + pickupOrderId + " status updated to PLACED (simulated).");
        } else {
            fail("pickupOrderId was null, cannot simulate payment.");
        }

        System.out.println("Sub-step 7.3: Restaurant Admin Processing Pickup Order ID: " + pickupOrderId + "...");
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
        System.out.println("Order " + pickupOrderId + " status: DELIVERED (Picked Up). Step 7 complete.");
    }

    @Test
    @Order(8) // Changed order
    void step8_customerAddsItemToCart_DeliveryScenario() throws Exception {
        System.out.println("Step 8: Customer Adding Item for Delivery Scenario...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");
        assertNotNull(dynamicDeliveryMenuItemId, "Dynamic Delivery MenuItem ID is required for this step.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before delivery scenario item add.");

        mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, dynamicDeliveryMenuItemId, 1);

        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicDeliveryMenuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(1));

        mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());

        System.out.println("Step 8: Item " + dynamicDeliveryMenuItemId + " added to cart for delivery order by user " + CUSTOMER_USERNAME);
    }


    @Test
    @Order(9) // Changed order
    @Transactional
    void step9_customerPlacesAndRestaurantProcessesDeliveryOrder() throws Exception { // Renamed from step9And10
        System.out.println("Step 9: Customer Placing Order for Delivery & Restaurant Processing...");
        assertNotNull(customerToken, "Customer token is required.");

        MvcResult orderResult = mockMvc.perform(post("/api/v1/users/{userId}/orders/place-from-cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING_PAYMENT.name()))
                .andReturn();
        OrderResponse placedOrder = objectMapper.readValue(orderResult.getResponse().getContentAsString(), OrderResponse.class);
        deliveryOrderId = placedOrder.getId();
        assertNotNull(deliveryOrderId, "Delivery Order ID should not be null.");
        assertEquals(restaurantId, placedOrder.getRestaurantId());
        System.out.println("Sub-step 9.1: Delivery Order placed with ID: " + deliveryOrderId + ", Status: " + placedOrder.getStatus());

        if (deliveryOrderId != null) {
            System.out.println("Sub-step 9.2: Simulating payment success for delivery Order ID: " + deliveryOrderId);
            orderService.processPaymentSuccess(deliveryOrderId, "test_pi_delivery_" + UUID.randomUUID().toString());
            System.out.println("Delivery Order ID: " + deliveryOrderId + " status updated to PLACED (simulated).");
        } else {
            fail("deliveryOrderId was null, cannot simulate payment.");
        }

        System.out.println("Sub-step 9.3: Restaurant Admin Processing Delivery Order ID: " + deliveryOrderId + "...");
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
        System.out.println("Order " + deliveryOrderId + " status: DELIVERED (Delivery Completed). Step 9 complete.");
    }


    @Test
    @Order(10) // Changed order
    void step10_cartManipulation_AddItemAgain() throws Exception { // Renamed from step11
        System.out.println("Step 10: Cart Manipulation - Adding item again...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(restaurantId, "Restaurant ID is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart cleared for user " + CUSTOMER_USERNAME + " before step 10.");

        AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, dynamicPickupMenuItemId, 1);
        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicPickupMenuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
        System.out.println("Step 10: Item added to cart.");
    }


    @Test
    @Order(11) // Changed order
    void step11_cartManipulation_UpdateCartItem() throws Exception { // Renamed from step12
        System.out.println("Step 11: Cart Manipulation - Updating cart item...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required.");

        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);
        mockMvc.perform(put("/api/v1/users/{userId}/cart/items/{menuItemId}", CUSTOMER_USERNAME, dynamicPickupMenuItemId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicPickupMenuItemId))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        System.out.println("Step 11: Cart item updated.");
    }

    @Test
    @Order(12) // Changed order
    void step12_cartManipulation_RemoveCartItem() throws Exception { // Renamed from step13
        System.out.println("Step 12: Cart Manipulation - Removing cart item...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart/items/{menuItemId}", CUSTOMER_USERNAME, dynamicPickupMenuItemId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        System.out.println("Step 12: Cart item removed, cart should be empty.");
    }

    @Test
    @Order(13) // Changed order
    void step13_cartManipulation_AddItemsFromDifferentRestaurant() throws Exception { // Renamed from step14
        System.out.println("Step 13: Cart Manipulation - Testing multi-restaurant behavior...");
        assertNotNull(adminToken, "Admin token is required for this step.");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID for first restaurant is required.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart explicitly cleared before testing multi-restaurant behavior");

        // Create a second restaurant and its admin/menu/menuitem
        String secondRestaurantAdminUsername = "e2eResAdmin2_" + UUID.randomUUID().toString().substring(0, 6);
        registerUserHelper(secondRestaurantAdminUsername, secondRestaurantAdminUsername + "@example.com", USER_PASSWORD, Set.of(Role.RESTAURANT_ADMIN));
        String secondRestaurantAdminToken = loginAndGetTokenHelper(secondRestaurantAdminUsername, USER_PASSWORD);
        UserResponseDto secondResAdminUser = objectMapper.readValue(mockMvc.perform(get("/api/v1/users/username/" + secondRestaurantAdminUsername)
                .header("Authorization", "Bearer " + adminToken)).andReturn().getResponse().getContentAsString(), UserResponseDto.class);

        String uniqueSecondRestaurantName = "Second E2E Test Restaurant " + UUID.randomUUID().toString().substring(0, 4);
        CreateRestaurantRequestDto createSecondRestaurantDto = new CreateRestaurantRequestDto(
                uniqueSecondRestaurantName,
                "Second restaurant for cart test", "456 Test Avenue", "555-5678",
                "second_e2e_restaurant_" + UUID.randomUUID().toString().substring(0, 4) + "@example.com");
        MvcResult secondRestaurantResult = mockMvc.perform(post("/api/v1/restaurants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSecondRestaurantDto)))
                .andExpect(status().isCreated()).andReturn();
        RestaurantResponseDto secondRestaurantResponse = objectMapper.readValue(secondRestaurantResult.getResponse().getContentAsString(), RestaurantResponseDto.class);
        secondRestaurantId = secondRestaurantResponse.getId(); // Store static var
        secondRestaurantName = secondRestaurantResponse.getName(); // Store static var
        assertNotNull(secondRestaurantId, "Second restaurant ID should not be null");

        UpdateRestaurantRequestDto updateSecondRestaurantDto = new UpdateRestaurantRequestDto();
        updateSecondRestaurantDto.setAdminUserIds(Set.of(secondResAdminUser.getId()));
        mockMvc.perform(put("/api/v1/restaurants/" + secondRestaurantId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateSecondRestaurantDto))).andExpect(status().isOk());

        CreateMenuRequestDto createSecondMenuDto = new CreateMenuRequestDto("Second E2E Menu", "Menu for second restaurant", secondRestaurantId);
        MvcResult secondMenuResult = mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + secondRestaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSecondMenuDto)))
                .andExpect(status().isCreated()).andReturn();
        MenuResponseDto secondMenu = objectMapper.readValue(secondMenuResult.getResponse().getContentAsString(), MenuResponseDto.class);
        Long secondMenuId = secondMenu.getId();

        CreateMenuItemRequestDto secondMenuItemDto = new CreateMenuItemRequestDto(
                dynamicSecondRestaurantItemName, "Item from another place", dynamicSecondRestaurantItemPrice, null, secondMenuId, null, true);
        MvcResult secondMenuItemResult = mockMvc.perform(post("/api/v1/menu-items")
                        .header("Authorization", "Bearer " + secondRestaurantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondMenuItemDto)))
                .andExpect(status().isCreated()).andReturn();
        MenuItemResponseDto secondMenuItem = objectMapper.readValue(secondMenuItemResult.getResponse().getContentAsString(), MenuItemResponseDto.class);
        dynamicSecondRestaurantItemId = secondMenuItem.getId();
        assertNotNull(dynamicSecondRestaurantItemId, "Dynamic Second Restaurant MenuItem ID should not be null");
        // Add to stub
        stubMenuServiceClient.addMenuItemDetails(new MenuItemDetailsDto(
                dynamicSecondRestaurantItemId, dynamicSecondRestaurantItemName, dynamicSecondRestaurantItemPrice, secondRestaurantId, secondRestaurantName, true));
        System.out.println("Created second restaurant with ID: " + secondRestaurantId + ", Menu ID: " + secondMenuId + ", MenuItem ID: " + dynamicSecondRestaurantItemId + " and added to stub.");


        AddItemToCartRequest firstRestaurantItem = new AddItemToCartRequest(restaurantId, dynamicPickupMenuItemId, 2);
        MvcResult firstCartResult = mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRestaurantItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicPickupMenuItemId))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andReturn();
        CartResponse cart1 = objectMapper.readValue(firstCartResult.getResponse().getContentAsString(), CartResponse.class);
        System.out.println("Added item from first restaurant, cart now contains " + cart1.getItems().size() + " item(s)");

        AddItemToCartRequest secondRestaurantItemRequest = new AddItemToCartRequest(secondRestaurantId, dynamicSecondRestaurantItemId, 1);
        MvcResult secondCartResult = mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRestaurantItemRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(secondRestaurantId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicSecondRestaurantItemId))
                .andReturn();
        CartResponse cart2 = objectMapper.readValue(secondCartResult.getResponse().getContentAsString(), CartResponse.class);
        System.out.println("Added item from second restaurant, cart now contains " +
                cart2.getItems().size() + " item(s) from restaurant: " + (cart2.getRestaurantName() != null ? cart2.getRestaurantName() : "unknown"));

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Step 13: Cart cleared for next test.");
    }

    @Test
    @Order(14) // Changed order
    void step14_cartManipulation_MultipleItemsAndTotalCalculation() throws Exception { // Renamed from step15
        System.out.println("Step 14: Cart Manipulation - Adding multiple items and verifying total calculation...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required.");
        assertNotNull(dynamicDeliveryMenuItemId, "Dynamic Delivery MenuItem ID is required.");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        System.out.println("Cart explicitly cleared before testing multiple items");

        mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        AddItemToCartRequest firstItem = new AddItemToCartRequest(restaurantId, dynamicPickupMenuItemId, 2);
        mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[0].menuItemId").value(dynamicPickupMenuItemId));

        AddItemToCartRequest secondItem = new AddItemToCartRequest(restaurantId, dynamicDeliveryMenuItemId, 3);
        MvcResult secondItemResult = mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();
        CartResponse cartWithBothItems = objectMapper.readValue(secondItemResult.getResponse().getContentAsString(), CartResponse.class);

        System.out.println("Cart now contains " + cartWithBothItems.getItems().size() + " items");
        assertEquals(2, cartWithBothItems.getItems().size(), "Cart should contain two distinct items.");

        BigDecimal itemsTotal = cartWithBothItems.getItems().stream()
                .map(CartItemResponse::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, itemsTotal.compareTo(cartWithBothItems.getCartTotalPrice()), "Cart total should equal sum of item totals");
        System.out.println("Step 14: Cart total verification successful.");
    }

    @Test
    @Order(15) // Changed order
    void step15_cartManipulation_GetCartAfterManipulation() throws Exception { // Renamed from step16
        System.out.println("Step 15: Cart Manipulation - Getting cart after manipulation...");
        assertNotNull(customerToken, "Customer token is required.");
        assertNotNull(dynamicPickupMenuItemId, "Dynamic Pickup MenuItem ID is required.");

        MvcResult cartResult = mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        CartResponse cart = objectMapper.readValue(cartResult.getResponse().getContentAsString(), CartResponse.class);
        System.out.println("Retrieved cart initially has " + cart.getItems().size() + " items");

        if (cart.getItems().isEmpty()) {
            System.out.println("Adding an item to cart for update test since cart is empty");
            AddItemToCartRequest addItemRequest = new AddItemToCartRequest(restaurantId, dynamicPickupMenuItemId, 1);
            mockMvc.perform(post("/api/v1/users/{userId}/cart/items", CUSTOMER_USERNAME)
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addItemRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isNotEmpty());
        }

        Long menuItemIdToUpdate = dynamicPickupMenuItemId;
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(10);
        mockMvc.perform(put("/api/v1/users/{userId}/cart/items/{menuItemId}", CUSTOMER_USERNAME, menuItemIdToUpdate)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
        System.out.println("Updated item " + menuItemIdToUpdate + " quantity to 10");

        MvcResult updatedCartResult = mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        CartResponse retrievedCart = objectMapper.readValue(updatedCartResult.getResponse().getContentAsString(), CartResponse.class);

        Optional<CartItemResponse> updatedItem = retrievedCart.getItems().stream()
                .filter(item -> item.getMenuItemId().equals(menuItemIdToUpdate))
                .findFirst();

        assertTrue(updatedItem.isPresent(), "Updated item with ID " + menuItemIdToUpdate + " should be found in the cart.");
        assertEquals(10, updatedItem.get().getQuantity(), "Item quantity should be updated to 10");
        System.out.println("Verified cart persistence with updated item quantity");

        mockMvc.perform(delete("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/{userId}/cart", CUSTOMER_USERNAME)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        System.out.println("Step 15: Cart cleared and verified empty.");
    }

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

    private String loginAndGetTokenHelper(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        JwtAuthenticationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), JwtAuthenticationResponse.class);
        return response.getAccessToken();
    }
}
