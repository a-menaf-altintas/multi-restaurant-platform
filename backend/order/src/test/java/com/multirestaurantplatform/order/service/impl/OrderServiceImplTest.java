// File: backend/order/src/test/java/com/multirestaurantplatform/order/service/impl/OrderServiceImplTest.java
package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.order.service.CartService; // Import CartService
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CartService cartService; // Mock CartService

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order testOrder;
    private User customerUserEntity; // Renamed for clarity
    private User adminUserEntity;    // For ADMIN role tests
    private User restaurantAdminUserEntity;
    private User anotherUserEntity;
    private Restaurant testRestaurant;

    private UserDetails customerPrincipal;
    private UserDetails adminPrincipal;
    private UserDetails restaurantAdminPrincipal;
    private UserDetails unauthorizedPrincipal;


    private final Long DEFAULT_ORDER_ID = 1L;
    private final Long DEFAULT_RESTAURANT_ID = 100L;
    private final String CUSTOMER_USERNAME = "customer1";
    private final String ADMIN_USERNAME_GLOBAL = "superAdmin"; // Different from restaurant admin
    private final String RESTAURANT_ADMIN_USERNAME = "resAdmin";
    private final String OTHER_USERNAME = "otherUser";


    @BeforeEach
    void setUp() {
        customerUserEntity = new User();
        customerUserEntity.setId(10L);
        customerUserEntity.setUsername(CUSTOMER_USERNAME);
        customerUserEntity.setPassword("password");
        customerUserEntity.setRoles(Set.of(Role.CUSTOMER));

        adminUserEntity = new User();
        adminUserEntity.setId(11L);
        adminUserEntity.setUsername(ADMIN_USERNAME_GLOBAL);
        adminUserEntity.setPassword("password");
        adminUserEntity.setRoles(Set.of(Role.ADMIN));

        restaurantAdminUserEntity = new User();
        restaurantAdminUserEntity.setId(1L);
        restaurantAdminUserEntity.setUsername(RESTAURANT_ADMIN_USERNAME);
        restaurantAdminUserEntity.setPassword("password");
        restaurantAdminUserEntity.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        anotherUserEntity = new User();
        anotherUserEntity.setId(2L);
        anotherUserEntity.setUsername(OTHER_USERNAME);
        anotherUserEntity.setPassword("password");
        anotherUserEntity.setRoles(Set.of(Role.CUSTOMER));


        testRestaurant = new Restaurant();
        testRestaurant.setId(DEFAULT_RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setRestaurantAdmins(Set.of(restaurantAdminUserEntity));

        testOrder = new Order();
        testOrder.setId(DEFAULT_ORDER_ID);
        testOrder.setRestaurantId(testRestaurant.getId());
        testOrder.setCreatedAt(LocalDateTime.now().minusHours(1));

        customerPrincipal = new org.springframework.security.core.userdetails.User(
                customerUserEntity.getUsername(), customerUserEntity.getPassword(),
                customerUserEntity.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toList())
        );
        adminPrincipal = new org.springframework.security.core.userdetails.User(
                adminUserEntity.getUsername(), adminUserEntity.getPassword(),
                adminUserEntity.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toList())
        );
        restaurantAdminPrincipal = new org.springframework.security.core.userdetails.User(
                restaurantAdminUserEntity.getUsername(), restaurantAdminUserEntity.getPassword(),
                restaurantAdminUserEntity.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toList())
        );
        unauthorizedPrincipal = new org.springframework.security.core.userdetails.User( // e.g. another customer trying to act on someone else's behalf
                anotherUserEntity.getUsername(), anotherUserEntity.getPassword(),
                anotherUserEntity.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toList())
        );


        lenient().when(userRepository.findByUsername(CUSTOMER_USERNAME)).thenReturn(Optional.of(customerUserEntity));
        lenient().when(userRepository.findByUsername(ADMIN_USERNAME_GLOBAL)).thenReturn(Optional.of(adminUserEntity));
        lenient().when(userRepository.findByUsername(RESTAURANT_ADMIN_USERNAME)).thenReturn(Optional.of(restaurantAdminUserEntity));
        lenient().when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity));
        lenient().when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
    }

    // ... (Keep existing Nested test classes for confirmOrder, markAsPreparing, etc.)
    @Nested
    class ConfirmOrderTests { /* ... */ }
    @Nested
    class MarkAsPreparingTests { /* ... */ }
    @Nested
    class MarkAsReadyForPickupTests { /* ... */ }
    @Nested
    class MarkAsPickedUpTests { /* ... */ }
    @Nested
    class MarkAsOutForDeliveryTests { /* ... */ }
    @Nested
    class CompleteDeliveryTests { /* ... */ }


    @Nested
    class PlaceOrderFromCartTests {
        private CartResponse mockCartResponse;
        private CartItemResponse mockCartItem1;

        @BeforeEach
        void setupPlaceOrderTests() {
            mockCartItem1 = new CartItemResponse(101L, "Burger", 2, new BigDecimal("10.00"), new BigDecimal("20.00"));
            List<CartItemResponse> items = List.of(mockCartItem1);
            mockCartResponse = new CartResponse(CUSTOMER_USERNAME, DEFAULT_RESTAURANT_ID, "Test Restaurant", items, new BigDecimal("20.00"));
        }

        @Test
        void placeOrderFromCart_whenCustomerPlacesOwnOrder_shouldSucceed() {
            // Arrange
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis()); // Assign a mock ID
                return order;
            });
            doNothing().when(cartService).clearCart(CUSTOMER_USERNAME);

            // Act
            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal);

            // Assert
            assertNotNull(placedOrder);
            assertEquals(customerUserEntity.getId(), placedOrder.getCustomerId());
            assertEquals(DEFAULT_RESTAURANT_ID, placedOrder.getRestaurantId());
            assertEquals(OrderStatus.PLACED, placedOrder.getStatus());
            assertNotNull(placedOrder.getPlacedAt());
            assertEquals(new BigDecimal("20.00"), placedOrder.getTotalPrice());
            assertEquals(1, placedOrder.getOrderItems().size());
            assertEquals("Burger", placedOrder.getOrderItems().get(0).getMenuItemName());

            verify(cartService).getCart(CUSTOMER_USERNAME);
            verify(orderRepository).save(any(Order.class));
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }

        @Test
        void placeOrderFromCart_whenAdminPlacesOrderForUser_shouldSucceed() {
            // Arrange
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis());
                return order;
            });
            doNothing().when(cartService).clearCart(CUSTOMER_USERNAME);

            // Act
            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, adminPrincipal); // Admin principal

            // Assert (similar to above)
            assertNotNull(placedOrder);
            assertEquals(customerUserEntity.getId(), placedOrder.getCustomerId());
            assertEquals(OrderStatus.PLACED, placedOrder.getStatus());
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }

        @Test
        void placeOrderFromCart_whenCartIsEmpty_shouldThrowIllegalOrderStateException() {
            // Arrange
            CartResponse emptyCart = new CartResponse(CUSTOMER_USERNAME, DEFAULT_RESTAURANT_ID, "Test Rest", List.of(), BigDecimal.ZERO);
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(emptyCart);

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal);
            });
            verify(cartService).getCart(CUSTOMER_USERNAME);
            verify(orderRepository, never()).save(any(Order.class));
            verify(cartService, never()).clearCart(anyString());
        }

        @Test
        void placeOrderFromCart_whenCartHasNoRestaurant_shouldThrowIllegalOrderStateException() {
            // Arrange
            mockCartResponse.setRestaurantId(null); // Simulate cart without restaurant
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal);
            });
        }

        @Test
        void placeOrderFromCart_whenCustomerTriesToPlaceOrderForAnotherUser_shouldThrowAccessDeniedException() {
            // Arrange
            // customerPrincipal (customer1) trying to place order for OTHER_USERNAME
            when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity)); // Ensure target user exists

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> {
                orderService.placeOrderFromCart(OTHER_USERNAME, customerPrincipal);
            });
            verify(cartService, never()).getCart(anyString());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void placeOrderFromCart_whenTargetUserNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            String nonExistentUsername = "ghostUser";
            when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.placeOrderFromCart(nonExistentUsername, adminPrincipal); // Admin tries for non-existent user
            });
        }

        @Test
        void placeOrderFromCart_clearCartFails_shouldStillPlaceOrderAndLogError() {
            // This test is more about observing logs, but we can verify order is saved.
            // Arrange
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis());
                return order;
            });
            // Simulate cartService.clearCart throwing an exception
            doThrow(new RuntimeException("Failed to clear cart (simulated error)")).when(cartService).clearCart(CUSTOMER_USERNAME);

            // Act
            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal);

            // Assert
            assertNotNull(placedOrder); // Order should still be placed
            assertEquals(OrderStatus.PLACED, placedOrder.getStatus());
            verify(orderRepository).save(any(Order.class));
            verify(cartService).clearCart(CUSTOMER_USERNAME); // Verify clearCart was called
            // Check logs manually or use a log appender if strict log verification is needed.
            // For now, we assume the LOGGER.error in the service was hit.
        }
    }
}
