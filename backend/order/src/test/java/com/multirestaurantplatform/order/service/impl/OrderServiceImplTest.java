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

        @Test
        void confirmOrder_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> orderService.confirmOrder(DEFAULT_ORDER_ID, adminPrincipal));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void confirmOrder_whenUserNotAuthorizedForRestaurant_shouldThrowAccessDeniedException() {
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            assertThrows(AccessDeniedException.class, () -> orderService.confirmOrder(DEFAULT_ORDER_ID, unauthorizedPrincipal));
        }

        @Test
        void confirmOrder_whenOrderNotPlaced_shouldThrowIllegalOrderStateException() {
            testOrder.setStatus(OrderStatus.CONFIRMED); // Already confirmed
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            assertThrows(IllegalOrderStateException.class, () -> orderService.confirmOrder(DEFAULT_ORDER_ID, adminPrincipal));
        }
    }

    @Nested
    class MarkAsPreparingTests {
        @BeforeEach
        void setupMarkAsPreparingTests() {
            // For these tests, the order should start as CONFIRMED
            testOrder.setStatus(OrderStatus.CONFIRMED);
            testOrder.setConfirmedAt(LocalDateTime.now().minusMinutes(30));
        }

        @Test
        void markAsPreparing_whenOrderIsValidAndUserAuthorized_shouldSetStatusToPreparing() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            // Authorization mocks are in global BeforeEach and should apply
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order preparingOrder = orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);

            // Assert
            assertNotNull(preparingOrder);
            assertEquals(OrderStatus.PREPARING, preparingOrder.getStatus());
            assertNotNull(preparingOrder.getPreparingAt()); // Check if Order's setStatus logic worked for preparingAt
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(userRepository).findByUsername(ADMIN_USERNAME);
            verify(restaurantRepository).findById(DEFAULT_RESTAURANT_ID);
            verify(orderRepository).save(testOrder);
        }

        @Test
        void markAsPreparing_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsPreparing_whenPrincipalUserNotFoundInRepo_shouldThrowUsernameNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.empty()); // User not found

            // Act & Assert
            assertThrows(UsernameNotFoundException.class, () -> {
                orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);
            });
        }

        @Test
        void markAsPreparing_whenRestaurantNotFoundForAuth_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            // User is found, but restaurant is not
            when(restaurantRepository.findById(DEFAULT_RESTAURANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);
            });
        }

        @Test
        void markAsPreparing_whenUserNotAuthorizedForRestaurant_shouldThrowAccessDeniedException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            // User 'anotherUserEntity' is not an admin of 'testRestaurant'

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> {
                orderService.markAsPreparing(DEFAULT_ORDER_ID, unauthorizedPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            verify(userRepository).findByUsername(OTHER_USERNAME); // Check that the correct username was looked up
            verify(restaurantRepository).findById(DEFAULT_RESTAURANT_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void markAsPreparing_whenOrderNotConfirmed_shouldThrowIllegalOrderStateException() {
            // Arrange
            testOrder.setStatus(OrderStatus.PLACED); // Set to a non-CONFIRMED state
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            // Authorization part will be called but the state check should fail first.

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.markAsPreparing(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository).findById(DEFAULT_ORDER_ID);
            // Authorization might still be checked depending on order of operations in service
            // verify(userRepository).findByUsername(ADMIN_USERNAME);
            // verify(restaurantRepository).findById(DEFAULT_RESTAURANT_ID);
            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}
