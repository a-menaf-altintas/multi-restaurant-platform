// File: backend/order/src/test/java/com/multirestaurantplatform/order/service/impl/OrderServiceImplTest.java
package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.PlaceOrderRequestDto; // Added
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.order.service.CartService;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
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
    private CartService cartService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order testOrder;
    private User customerUserEntity;
    private User adminUserEntity;
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
    private final String ADMIN_USERNAME_GLOBAL = "superAdmin";
    private final String RESTAURANT_ADMIN_USERNAME = "resAdmin";
    private final String OTHER_USERNAME = "otherUser";

    private PlaceOrderRequestDto samplePlaceOrderRequestDto;

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
        unauthorizedPrincipal = new org.springframework.security.core.userdetails.User(
                anotherUserEntity.getUsername(), anotherUserEntity.getPassword(),
                anotherUserEntity.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toList())
        );

        samplePlaceOrderRequestDto = new PlaceOrderRequestDto(
                "123 Main St", "Apt 4B", "Anytown", "CA", "90210", "USA",
                "555-1234", "Leave at the door."
        );

        lenient().when(userRepository.findByUsername(CUSTOMER_USERNAME)).thenReturn(Optional.of(customerUserEntity));
        lenient().when(userRepository.findByUsername(ADMIN_USERNAME_GLOBAL)).thenReturn(Optional.of(adminUserEntity));
        lenient().when(userRepository.findByUsername(RESTAURANT_ADMIN_USERNAME)).thenReturn(Optional.of(restaurantAdminUserEntity));
        lenient().when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity));
        lenient().when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
    }

    // --- Nested test classes for other OrderService methods ---
    @Nested
    @DisplayName("Confirm Order Tests")
    class ConfirmOrderTests {
        @BeforeEach
        void setupConfirmOrder() {
            testOrder.setStatus(OrderStatus.PLACED); // Prerequisite for confirm
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("Confirm Order - Success by Restaurant Admin")
        void confirmOrder_byRestaurantAdmin_shouldSucceed() {
            Order confirmedOrder = orderService.confirmOrder(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
            assertNotNull(confirmedOrder.getConfirmedAt());
            verify(orderRepository).save(testOrder);
        }

        @Test
        @DisplayName("Confirm Order - Wrong Status (Not PLACED)")
        void confirmOrder_whenOrderStatusNotPlaced_shouldThrowIllegalOrderStateException() {
            testOrder.setStatus(OrderStatus.PREPARING); // Set to a non-PLACE status
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.confirmOrder(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            });
        }
        // Add more tests for authorization (e.g. non-admin trying to confirm)
    }

    @Nested
    @DisplayName("Mark As Preparing Tests")
    class MarkAsPreparingTests {
        @BeforeEach
        void setupMarkAsPreparing() {
            testOrder.setStatus(OrderStatus.CONFIRMED);
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        @Test
        @DisplayName("Mark As Preparing - Success")
        void markAsPreparing_whenOrderConfirmed_shouldSucceed() {
            Order preparingOrder = orderService.markAsPreparing(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.PREPARING, preparingOrder.getStatus());
            assertNotNull(preparingOrder.getPreparingAt());
            verify(orderRepository).save(testOrder);
        }
    }

    @Nested
    @DisplayName("Mark As Ready For Pickup Tests")
    class MarkAsReadyForPickupTests {
        @BeforeEach
        void setupMarkAsReady() {
            testOrder.setStatus(OrderStatus.PREPARING);
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        @Test
        @DisplayName("Mark As Ready For Pickup - Success")
        void markAsReadyForPickup_whenOrderIsPreparing_shouldSucceed() {
            Order readyOrder = orderService.markAsReadyForPickup(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.READY_FOR_PICKUP, readyOrder.getStatus());
            assertNotNull(readyOrder.getReadyAt());
            verify(orderRepository).save(testOrder);
        }
    }

    @Nested
    @DisplayName("Mark As Picked Up Tests")
    class MarkAsPickedUpTests {
        @BeforeEach
        void setupMarkAsPickedUp() {
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        @Test
        @DisplayName("Mark As Picked Up - Success")
        void markAsPickedUp_whenOrderIsReadyForPickup_shouldSucceed() {
            Order pickedUpOrder = orderService.markAsPickedUp(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.DELIVERED, pickedUpOrder.getStatus());
            assertNotNull(pickedUpOrder.getDeliveredAt());
            verify(orderRepository).save(testOrder);
        }
    }

    @Nested
    @DisplayName("Mark As Out For Delivery Tests")
    class MarkAsOutForDeliveryTests {
        @BeforeEach
        void setupMarkAsOutForDelivery() {
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP); // Can also be PREPARING based on logic
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        @Test
        @DisplayName("Mark As Out For Delivery - Success from READY_FOR_PICKUP")
        void markAsOutForDelivery_whenOrderIsReadyForPickup_shouldSucceed() {
            Order outOrder = orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.OUT_FOR_DELIVERY, outOrder.getStatus());
            assertNotNull(outOrder.getOutForDeliveryAt());
            verify(orderRepository).save(testOrder);
        }
    }

    @Nested
    @DisplayName("Complete Delivery Tests")
    class CompleteDeliveryTests {
        @BeforeEach
        void setupCompleteDelivery() {
            testOrder.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            lenient().when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }
        @Test
        @DisplayName("Complete Delivery - Success")
        void completeDelivery_whenOrderIsOutForDelivery_shouldSucceed() {
            Order deliveredOrder = orderService.completeDelivery(DEFAULT_ORDER_ID, restaurantAdminPrincipal);
            assertEquals(OrderStatus.DELIVERED, deliveredOrder.getStatus());
            assertNotNull(deliveredOrder.getDeliveredAt());
            verify(orderRepository).save(testOrder);
        }
    }


    @Nested
    @DisplayName("Place Order From Cart Tests")
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
        @DisplayName("Place Order - Customer Places Own Order (No Address DTO)")
        void placeOrderFromCart_whenCustomerPlacesOwnOrder_shouldSucceed() {
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis());
                return order;
            });
            doNothing().when(cartService).clearCart(CUSTOMER_USERNAME);

            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal, null); // Pass null for DTO

            assertNotNull(placedOrder);
            assertEquals(customerUserEntity.getId(), placedOrder.getCustomerId());
            assertEquals(DEFAULT_RESTAURANT_ID, placedOrder.getRestaurantId());
            assertEquals(OrderStatus.PENDING_PAYMENT, placedOrder.getStatus());
            assertEquals(new BigDecimal("20.00"), placedOrder.getTotalPrice());
            assertEquals(1, placedOrder.getOrderItems().size());
            assertEquals("Burger", placedOrder.getOrderItems().get(0).getMenuItemName());
            assertNull(placedOrder.getDeliveryAddressLine1(), "Delivery address should be null when DTO is null");


            verify(cartService).getCart(CUSTOMER_USERNAME);
            verify(orderRepository).save(any(Order.class));
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }

        @Test
        @DisplayName("Place Order - With Delivery Address DTO")
        void placeOrderFromCart_withDeliveryAddress_shouldSaveAddressDetails() {
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            // Ensure save returns the captured order with an ID for further assertions if needed
            when(orderRepository.save(orderCaptor.capture())).thenAnswer(invocation -> {
                Order orderToSave = invocation.getArgument(0);
                if (orderToSave.getId() == null) { // Simulate ID generation if not already set
                    orderToSave.setId(System.currentTimeMillis());
                }
                return orderToSave;
            });
            doNothing().when(cartService).clearCart(CUSTOMER_USERNAME);

            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal, samplePlaceOrderRequestDto);

            assertNotNull(placedOrder);
            assertEquals(OrderStatus.PENDING_PAYMENT, placedOrder.getStatus());

            Order capturedOrder = orderCaptor.getValue();
            assertEquals(samplePlaceOrderRequestDto.getDeliveryAddressLine1(), capturedOrder.getDeliveryAddressLine1());
            assertEquals(samplePlaceOrderRequestDto.getDeliveryCity(), capturedOrder.getDeliveryCity());
            assertEquals(samplePlaceOrderRequestDto.getDeliveryPostalCode(), capturedOrder.getDeliveryPostalCode());
            assertEquals(samplePlaceOrderRequestDto.getCustomerContactNumber(), capturedOrder.getCustomerContactNumber());
            assertEquals(samplePlaceOrderRequestDto.getSpecialInstructions(), capturedOrder.getSpecialInstructions());

            verify(cartService).getCart(CUSTOMER_USERNAME);
            verify(orderRepository).save(any(Order.class));
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }


        @Test
        @DisplayName("Place Order - Admin Places Order For User (No Address DTO)")
        void placeOrderFromCart_whenAdminPlacesOrderForUser_shouldSucceed() {
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis());
                return order;
            });
            doNothing().when(cartService).clearCart(CUSTOMER_USERNAME);

            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, adminPrincipal, null);

            assertNotNull(placedOrder);
            assertEquals(customerUserEntity.getId(), placedOrder.getCustomerId());
            assertEquals(OrderStatus.PENDING_PAYMENT, placedOrder.getStatus());
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }

        @Test
        @DisplayName("Place Order - Cart Is Empty")
        void placeOrderFromCart_whenCartIsEmpty_shouldThrowIllegalOrderStateException() {
            CartResponse emptyCart = new CartResponse(CUSTOMER_USERNAME, DEFAULT_RESTAURANT_ID, "Test Rest", List.of(), BigDecimal.ZERO);
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(emptyCart);

            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal, null);
            });
            verify(cartService).getCart(CUSTOMER_USERNAME);
            verify(orderRepository, never()).save(any(Order.class));
            verify(cartService, never()).clearCart(anyString());
        }

        @Test
        @DisplayName("Place Order - Cart Has No Restaurant")
        void placeOrderFromCart_whenCartHasNoRestaurant_shouldThrowIllegalOrderStateException() {
            mockCartResponse.setRestaurantId(null);
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);

            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal, null);
            });
        }

        @Test
        @DisplayName("Place Order - Customer Tries For Another User")
        void placeOrderFromCart_whenCustomerTriesToPlaceOrderForAnotherUser_shouldThrowAccessDeniedException() {
            when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity));

            assertThrows(AccessDeniedException.class, () -> {
                orderService.placeOrderFromCart(OTHER_USERNAME, customerPrincipal, null);
            });
            verify(cartService, never()).getCart(anyString());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Place Order - Target User Not Found")
        void placeOrderFromCart_whenTargetUserNotFound_shouldThrowResourceNotFoundException() {
            String nonExistentUsername = "ghostUser";
            when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.placeOrderFromCart(nonExistentUsername, adminPrincipal, null);
            });
        }

        @Test
        @DisplayName("Place Order - Clear Cart Fails (Still Places Order)")
        void placeOrderFromCart_clearCartFails_shouldStillPlaceOrderAndLogError() {
            when(cartService.getCart(CUSTOMER_USERNAME)).thenReturn(mockCartResponse);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(System.currentTimeMillis());
                return order;
            });
            doThrow(new RuntimeException("Failed to clear cart (simulated error)")).when(cartService).clearCart(CUSTOMER_USERNAME);

            Order placedOrder = orderService.placeOrderFromCart(CUSTOMER_USERNAME, customerPrincipal, null);

            assertNotNull(placedOrder);
            assertEquals(OrderStatus.PENDING_PAYMENT, placedOrder.getStatus());
            verify(orderRepository).save(any(Order.class));
            verify(cartService).clearCart(CUSTOMER_USERNAME);
        }
    }
}
