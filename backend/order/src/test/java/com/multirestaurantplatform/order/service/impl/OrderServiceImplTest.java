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
    private UserRepository userRepository; // Mock UserRepository

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order testOrder;
    private User restaurantAdminUserEntity; // The actual User entity for the admin
    private User anotherUserEntity;         // The actual User entity for another user
    private Restaurant testRestaurant;
    private UserDetails adminPrincipal;      // Spring Security UserDetails for the admin
    private UserDetails unauthorizedPrincipal; // Spring Security UserDetails for unauthorized user

    @BeforeEach
    void setUp() {
        // Setup Restaurant Admin User Entity
        restaurantAdminUserEntity = new User();
        restaurantAdminUserEntity.setId(1L);
        restaurantAdminUserEntity.setUsername("resAdmin");
        restaurantAdminUserEntity.setPassword("password"); // Needed for UserDetails creation
        restaurantAdminUserEntity.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        // Setup Another User Entity
        anotherUserEntity = new User();
        anotherUserEntity.setId(2L);
        anotherUserEntity.setUsername("otherUser");
        anotherUserEntity.setPassword("password");
        anotherUserEntity.setRoles(Set.of(Role.RESTAURANT_ADMIN)); // Could be any role for testing non-association

        // Setup Restaurant
        testRestaurant = new Restaurant();
        testRestaurant.setId(100L);
        testRestaurant.setName("Test Restaurant");
        // IMPORTANT: Associate the actual User entity with the restaurant
        testRestaurant.setRestaurantAdmins(Set.of(restaurantAdminUserEntity));

        // Setup Order
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setRestaurantId(testRestaurant.getId());
        testOrder.setStatus(OrderStatus.PLACED);
        testOrder.setCreatedAt(LocalDateTime.now().minusHours(1));
        testOrder.setPlacedAt(LocalDateTime.now().minusHours(1));

        // Setup Spring Security UserDetails for the authorized admin
        adminPrincipal = new org.springframework.security.core.userdetails.User(
                restaurantAdminUserEntity.getUsername(),
                restaurantAdminUserEntity.getPassword(),
                restaurantAdminUserEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );

        // Setup Spring Security UserDetails for an unauthorized user
        unauthorizedPrincipal = new org.springframework.security.core.userdetails.User(
                anotherUserEntity.getUsername(),
                anotherUserEntity.getPassword(),
                anotherUserEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );
    }

    @Test
    void confirmOrder_whenOrderIsValidAndUserAuthorized_shouldConfirmOrder() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        // Mock fetching the admin User entity by username
        when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.of(restaurantAdminUserEntity));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order confirmedOrder = orderService.confirmOrder(1L, adminPrincipal);

        // Assert
        assertNotNull(confirmedOrder);
        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
        assertNotNull(confirmedOrder.getConfirmedAt());
        verify(orderRepository).findById(1L);
        verify(userRepository).findByUsername(adminPrincipal.getUsername());
        verify(restaurantRepository).findById(testRestaurant.getId());
        verify(orderRepository).save(testOrder);
    }

    @Test
    void confirmOrder_whenOrderNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.confirmOrder(1L, adminPrincipal);
        });
        verify(orderRepository).findById(1L);
        verify(userRepository, never()).findByUsername(anyString());
        verify(restaurantRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void confirmOrder_whenPrincipalUserNotFoundInRepo_shouldThrowUsernameNotFoundException() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.empty()); // User not found

        // Act & Assert
        assertThrows(UsernameNotFoundException.class, () -> {
            orderService.confirmOrder(1L, adminPrincipal);
        });
        verify(orderRepository).findById(1L);
        verify(userRepository).findByUsername(adminPrincipal.getUsername());
        verify(restaurantRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void confirmOrder_whenRestaurantNotFoundForAuth_shouldThrowResourceNotFoundException() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.of(restaurantAdminUserEntity));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.empty()); // Restaurant not found

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.confirmOrder(1L, adminPrincipal);
        });
        verify(orderRepository).findById(1L);
        verify(userRepository).findByUsername(adminPrincipal.getUsername());
        verify(restaurantRepository).findById(testRestaurant.getId());
        verify(orderRepository, never()).save(any(Order.class));
    }


    @Test
    void confirmOrder_whenUserNotAuthorizedForRestaurant_shouldThrowAccessDeniedException() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        // Mock fetching the 'anotherUserEntity' (who is not an admin of testRestaurant)
        when(userRepository.findByUsername(unauthorizedPrincipal.getUsername())).thenReturn(Optional.of(anotherUserEntity));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            orderService.confirmOrder(1L, unauthorizedPrincipal);
        });
        verify(orderRepository).findById(1L);
        verify(userRepository).findByUsername(unauthorizedPrincipal.getUsername());
        verify(restaurantRepository).findById(testRestaurant.getId());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void confirmOrder_whenOrderNotPlaced_shouldThrowIllegalOrderStateException() {
        // Arrange
        testOrder.setStatus(OrderStatus.PREPARING); // Set to a non-PLACED state
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        // Mock fetching the admin User entity by username for the authorization check part
        when(userRepository.findByUsername(adminPrincipal.getUsername())).thenReturn(Optional.of(restaurantAdminUserEntity));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));

        // Act & Assert
        assertThrows(IllegalOrderStateException.class, () -> {
            orderService.confirmOrder(1L, adminPrincipal);
        });
        verify(orderRepository).findById(1L);
        verify(userRepository).findByUsername(adminPrincipal.getUsername());
        verify(restaurantRepository).findById(testRestaurant.getId());
        verify(orderRepository, never()).save(any(Order.class));
    }
}
