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

        // Common mocks for authorization part, can be overridden in specific tests
        lenient().when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Optional.of(restaurantAdminUserEntity));
        lenient().when(userRepository.findByUsername(OTHER_USERNAME)).thenReturn(Optional.of(anotherUserEntity));
        lenient().when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
    }

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

            assertNotNull(confirmedOrder);
            assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
            assertNotNull(confirmedOrder.getConfirmedAt());
            verify(orderRepository).save(testOrder);
        }
        // ... other confirmOrder tests ...
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

            assertNotNull(preparingOrder);
            assertEquals(OrderStatus.PREPARING, preparingOrder.getStatus());
            assertNotNull(preparingOrder.getPreparingAt());
            verify(orderRepository).save(testOrder);
        }
        // ... other markAsPreparing tests ...
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

            assertNotNull(readyOrder);
            assertEquals(OrderStatus.READY_FOR_PICKUP, readyOrder.getStatus());
            assertNotNull(readyOrder.getReadyAt());
            verify(orderRepository).save(testOrder);
        }
        // ... other markAsReadyForPickup tests ...
    }

    @Nested
    class MarkAsPickedUpTests {
        @BeforeEach
        void setupMarkAsPickedUpTests() {
            // For these tests, the order should start as READY_FOR_PICKUP
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
            testOrder.setReadyAt(LocalDateTime.now().minusMinutes(5));
        }

        @Test
        void markAsPickedUp_whenOrderIsValidAndUserAuthorized_shouldSetStatusToDelivered() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order deliveredOrder = orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);

            // Assert
            assertNotNull(deliveredOrder);
            assertEquals(OrderStatus.DELIVERED, deliveredOrder.getStatus());
            assertNotNull(deliveredOrder.getDeliveredAt()); // Check if Order's setStatus logic worked for deliveredAt
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(userRepository).findByUsername(ADMIN_USERNAME);
            verify(restaurantRepository).findById(DEFAULT_RESTAURANT_ID);
            verify(orderRepository).save(testOrder);
        }

        @Test
        void markAsPickedUp_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsPickedUp_whenPrincipalUserNotFoundInRepo_shouldThrowUsernameNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UsernameNotFoundException.class, () -> {
                orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);
            });
        }

        @Test
        void markAsPickedUp_whenRestaurantNotFoundForAuth_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);
            });
        }

        @Test
        void markAsPickedUp_whenUserNotAuthorizedForRestaurant_shouldThrowAccessDeniedException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> {
                orderService.markAsPickedUp(DEFAULT_ORDER_ID, unauthorizedPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(userRepository).findByUsername(OTHER_USERNAME);
            verify(restaurantRepository).findById(DEFAULT_RESTAURANT_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsPickedUp_whenOrderNotReadyForPickup_shouldThrowIllegalOrderStateException() {
            // Arrange
            testOrder.setStatus(OrderStatus.PREPARING); // Set to a non-READY_FOR_PICKUP state
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.markAsPickedUp(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}
