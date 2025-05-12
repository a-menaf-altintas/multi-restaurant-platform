// File: backend/order/src/test/java/com/multirestaurantplatform/order/service/impl/OrderServiceImplTest.java
package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User; // Your User entity
import com.multirestaurantplatform.security.repository.UserRepository; // To mock user fetching

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;


import java.time.LocalDateTime;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order testOrder;
    private User restaurantAdminUserEntity;
    private User anotherUserEntity;
    private Restaurant testRestaurant;
    private UserDetails adminPrincipal;
    private UserDetails unauthorizedPrincipal;

    private final Long DEFAULT_ORDER_ID = 1L;
    private final Long DEFAULT_RESTAURANT_ID = 100L;
    private final String ADMIN_USERNAME = "resAdmin";
    private final String OTHER_USERNAME = "otherUser";


    @BeforeEach
    void setUp() {
        restaurantAdminUserEntity = new User();
        restaurantAdminUserEntity.setId(1L);
        restaurantAdminUserEntity.setUsername(ADMIN_USERNAME);
        restaurantAdminUserEntity.setPassword("password");
        restaurantAdminUserEntity.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        anotherUserEntity = new User();
        anotherUserEntity.setId(2L);
        anotherUserEntity.setUsername(OTHER_USERNAME);
        anotherUserEntity.setPassword("password");
        anotherUserEntity.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        testRestaurant = new Restaurant();
        testRestaurant.setId(DEFAULT_RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setRestaurantAdmins(Set.of(restaurantAdminUserEntity));

        testOrder = new Order();
        testOrder.setId(DEFAULT_ORDER_ID);
        testOrder.setRestaurantId(testRestaurant.getId());
        testOrder.setCreatedAt(LocalDateTime.now().minusHours(1));

        adminPrincipal = new org.springframework.security.core.userdetails.User(
                restaurantAdminUserEntity.getUsername(),
                restaurantAdminUserEntity.getPassword(),
                restaurantAdminUserEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );

        unauthorizedPrincipal = new org.springframework.security.core.userdetails.User(
                anotherUserEntity.getUsername(),
                anotherUserEntity.getPassword(),
                anotherUserEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );

        lenient().when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.of(restaurantAdminUserEntity));
        lenient().when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity));
        lenient().when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
    }

    // ... ConfirmOrderTests, MarkAsPreparingTests, MarkAsReadyForPickupTests, MarkAsPickedUpTests ...
    @Nested
    class ConfirmOrderTests {
        @BeforeEach
        void setupConfirmOrderTests() {
            testOrder.setStatus(OrderStatus.PLACED);
            testOrder.setPlacedAt(LocalDateTime.now().minusHours(1));
        }

        @Test
        void confirmOrder_whenOrderIsValidAndUserAuthorized_shouldConfirmOrder() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Order confirmedOrder = orderService.confirmOrder(DEFAULT_ORDER_ID, adminPrincipal);
            assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
            assertNotNull(confirmedOrder.getConfirmedAt());
        }
    }

    @Nested
    class MarkAsPreparingTests {
        @BeforeEach
        void setupMarkAsPreparingTests() {
            testOrder.setStatus(OrderStatus.CONFIRMED);
            testOrder.setConfirmedAt(LocalDateTime.now().minusMinutes(30));
        }

        @Test
        void markAsPreparing_whenOrderIsValidAndUserAuthorized_shouldSetStatusToPreparing() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Order preparingOrder = orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);
            assertEquals(OrderStatus.PREPARING, preparingOrder.getStatus());
            assertNotNull(preparingOrder.getPreparingAt());
        }
    }

    @Nested
    class MarkAsReadyForPickupTests {
        @BeforeEach
        void setupMarkAsReadyForPickupTests() {
            testOrder.setStatus(OrderStatus.PREPARING);
            testOrder.setPreparingAt(LocalDateTime.now().minusMinutes(15));
        }

        @Test
        void markAsReadyForPickup_whenOrderIsValidAndUserAuthorized_shouldSetStatusToReadyForPickup() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Order readyOrder = orderService.markAsReadyForPickup(DEFAULT_ORDER_ID, adminPrincipal);
            assertEquals(OrderStatus.READY_FOR_PICKUP, readyOrder.getStatus());
            assertNotNull(readyOrder.getReadyAt());
        }
    }

    @Nested
    class MarkAsPickedUpTests {
        @BeforeEach
        void setupMarkAsPickedUpTests() {
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
            testOrder.setReadyAt(LocalDateTime.now().minusMinutes(5));
        }

        @Test
        void markAsPickedUp_whenOrderIsValidAndUserAuthorized_shouldSetStatusToDelivered() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Order deliveredOrder = orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);
            assertEquals(OrderStatus.DELIVERED, deliveredOrder.getStatus());
            assertNotNull(deliveredOrder.getDeliveredAt());
        }
    }

    @Nested
    class MarkAsOutForDeliveryTests {
        @BeforeEach
        void setupMarkAsOutForDeliveryTests() {
            // Default starting state for these tests
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
            testOrder.setReadyAt(LocalDateTime.now().minusMinutes(10));
            testOrder.setOutForDeliveryAt(null); // Ensure it's null before test
        }

        @Test
        void markAsOutForDelivery_fromReadyForPickup_andUserAuthorized_shouldSetStatusToOutForDelivery() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order outForDeliveryOrder = orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, adminPrincipal);

            // Assert
            assertNotNull(outForDeliveryOrder);
            assertEquals(OrderStatus.OUT_FOR_DELIVERY, outForDeliveryOrder.getStatus());
            assertNotNull(outForDeliveryOrder.getOutForDeliveryAt());
            // readyAt should also be set if it wasn't already (logic in Order.setStatus)
            assertNotNull(outForDeliveryOrder.getReadyAt());
            verify(orderRepository).save(testOrder);
        }

        @Test
        void markAsOutForDelivery_fromPreparing_andUserAuthorized_shouldSetStatusToOutForDelivery() {
            // Arrange
            testOrder.setStatus(OrderStatus.PREPARING); // Set initial state to PREPARING
            testOrder.setPreparingAt(LocalDateTime.now().minusMinutes(20));
            testOrder.setReadyAt(null); // Ensure readyAt is null to test it gets set

            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order outForDeliveryOrder = orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, adminPrincipal);

            // Assert
            assertNotNull(outForDeliveryOrder);
            assertEquals(OrderStatus.OUT_FOR_DELIVERY, outForDeliveryOrder.getStatus());
            assertNotNull(outForDeliveryOrder.getOutForDeliveryAt());
            assertNotNull(outForDeliveryOrder.getReadyAt()); // Verify readyAt was also set
            verify(orderRepository).save(testOrder);
        }


        @Test
        void markAsOutForDelivery_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsOutForDelivery_whenUserNotAuthorized_shouldThrowAccessDeniedException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            // unauthorizedPrincipal is not an admin for testRestaurant

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> {
                orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, unauthorizedPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsOutForDelivery_whenOrderNotInReadyOrPreparingState_shouldThrowIllegalOrderStateException() {
            // Arrange
            testOrder.setStatus(OrderStatus.CONFIRMED); // Invalid starting state
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.markAsOutForDelivery(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}
