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

    // ... ConfirmOrderTests, MarkAsPreparingTests, MarkAsReadyForPickupTests, MarkAsPickedUpTests, MarkAsOutForDeliveryTests ...
    @Nested
    class ConfirmOrderTests {
        // ... tests ...
    }
    @Nested
    class MarkAsPreparingTests {
        // ... tests ...
    }
    @Nested
    class MarkAsReadyForPickupTests {
        // ... tests ...
    }
    @Nested
    class MarkAsPickedUpTests {
        // ... tests ...
    }
    @Nested
    class MarkAsOutForDeliveryTests {
        // ... tests ...
    }


    @Nested
    class CompleteDeliveryTests {
        @BeforeEach
        void setupCompleteDeliveryTests() {
            // For these tests, the order should start as OUT_FOR_DELIVERY
            testOrder.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            testOrder.setOutForDeliveryAt(LocalDateTime.now().minusMinutes(30));
            testOrder.setDeliveredAt(null); // Ensure it's null before test
        }

        @Test
        void completeDelivery_whenOrderIsOutForDeliveryAndUserAuthorized_shouldSetStatusToDelivered() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order deliveredOrder = orderService.completeDelivery(DEFAULT_ORDER_ID, adminPrincipal);

            // Assert
            assertNotNull(deliveredOrder);
            assertEquals(OrderStatus.DELIVERED, deliveredOrder.getStatus());
            assertNotNull(deliveredOrder.getDeliveredAt());
            verify(orderRepository).save(testOrder);
        }

        @Test
        void completeDelivery_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> {
                orderService.completeDelivery(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void completeDelivery_whenUserNotAuthorized_shouldThrowAccessDeniedException() {
            // Arrange
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> {
                orderService.completeDelivery(DEFAULT_ORDER_ID, unauthorizedPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void completeDelivery_whenOrderNotOutForDelivery_shouldThrowIllegalOrderStateException() {
            // Arrange
            testOrder.setStatus(OrderStatus.READY_FOR_PICKUP); // Invalid starting state
            when(orderRepository.findById(DEFAULT_ORDER_ID)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThrows(IllegalOrderStateException.class, () -> {
                orderService.completeDelivery(DEFAULT_ORDER_ID, adminPrincipal);
            });
            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}
