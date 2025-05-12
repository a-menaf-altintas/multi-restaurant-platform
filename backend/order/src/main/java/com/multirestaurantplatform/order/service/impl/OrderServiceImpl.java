// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/impl/OrderServiceImpl.java
package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.User; // Your User entity
import com.multirestaurantplatform.security.repository.UserRepository; // To fetch the User entity

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // For fetching user
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository; // Inject UserRepository

    @Override
    @Transactional
    public Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to confirm order with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    LOGGER.warn("Order confirmation failed: Order not found with ID: {}", orderId);
                    return new ResourceNotFoundException("Order not found with id: " + orderId);
                });

        Long orderRestaurantId = order.getRestaurantId(); // From your existing Order entity
        if (orderRestaurantId == null) {
            LOGGER.error("Order confirmation failed: Order ID {} is not associated with any restaurant.", orderId);
            throw new IllegalStateException("Order " + orderId + " has no associated restaurant ID.");
        }

        // --- Authorization Check ---
        String principalUsername = restaurantAdminPrincipal.getUsername();
        // Fetch the full User entity for the authenticated principal
        User adminUser = userRepository.findByUsername(principalUsername)
                .orElseThrow(() -> {
                    // This case should ideally be rare if the UserDetails came from a valid user session
                    LOGGER.warn("Authorization failed: User {} (from principal) not found in repository for order confirmation.", principalUsername);
                    return new UsernameNotFoundException("Authenticated user " + principalUsername + " not found in database.");
                });

        Restaurant restaurant = restaurantRepository.findById(orderRestaurantId)
                .orElseThrow(() -> {
                    LOGGER.warn("Restaurant ID {} (for order ID {}) not found during authorization.", orderRestaurantId, orderId);
                    return new ResourceNotFoundException("Restaurant not found with ID: " + orderRestaurantId + " for order " + orderId);
                });

        // Your Restaurant entity has Set<User> restaurantAdmins. Check if the fetched adminUser is in this set.
        boolean isAuthorized = restaurant.getRestaurantAdmins().stream()
                .anyMatch(admin -> admin.getId().equals(adminUser.getId()));

        if (!isAuthorized) {
            LOGGER.warn("Authorization failed: User {} (ID: {}) is not an admin for restaurant ID {} (Order ID: {})",
                    principalUsername, adminUser.getId(), orderRestaurantId, orderId);
            throw new AccessDeniedException("User " + principalUsername +
                    " is not authorized to confirm orders for restaurant ID " + orderRestaurantId);
        }
        LOGGER.info("User {} is authorized admin for restaurant ID {} of order ID {}",
                principalUsername, orderRestaurantId, orderId);
        // --- End Authorization Check ---

        if (order.getStatus() != OrderStatus.PLACED) {
            LOGGER.warn("Order confirmation failed: Order ID {} is not in PLACED state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be confirmed. Expected status PLACED, but was " + order.getStatus() + "."
            );
        }

        order.setStatus(OrderStatus.CONFIRMED); // This will also set 'confirmedAt' via your Order entity's setStatus method
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} confirmed successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());

        return savedOrder;
    }
}
