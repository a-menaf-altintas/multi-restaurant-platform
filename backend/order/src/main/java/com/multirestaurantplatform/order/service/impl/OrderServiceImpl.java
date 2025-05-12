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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to confirm order with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());

        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, restaurantAdminPrincipal);

        if (order.getStatus() != OrderStatus.PLACED) {
            LOGGER.warn("Order confirmation failed: Order ID {} is not in PLACED state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be confirmed. Expected status PLACED, but was " + order.getStatus() + "."
            );
        }

        order.setStatus(OrderStatus.CONFIRMED); // This will also set 'confirmedAt' via Order entity's setStatus
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} confirmed successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to mark order as PREPARING with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());

        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, restaurantAdminPrincipal);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            LOGGER.warn("Marking order as PREPARING failed: Order ID {} is not in CONFIRMED state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as preparing. Expected status CONFIRMED, but was " + order.getStatus() + "."
            );
        }

        order.setStatus(OrderStatus.PREPARING); // This will also set 'preparingAt' via Order entity's setStatus
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as PREPARING successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order markAsReadyForPickup(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to mark order as READY_FOR_PICKUP with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());

        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, restaurantAdminPrincipal);

        if (order.getStatus() != OrderStatus.PREPARING) {
            LOGGER.warn("Marking order as READY_FOR_PICKUP failed: Order ID {} is not in PREPARING state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as ready for pickup. Expected status PREPARING, but was " + order.getStatus() + "."
            );
        }

        order.setStatus(OrderStatus.READY_FOR_PICKUP); // This will also set 'readyAt' via Order entity's setStatus
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as READY_FOR_PICKUP successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    /**
     * Helper method to fetch an order by ID or throw ResourceNotFoundException.
     * @param orderId The ID of the order.
     * @return The found Order entity.
     */
    private Order findOrderByIdOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    LOGGER.warn("Order operation failed: Order not found with ID: {}", orderId);
                    return new ResourceNotFoundException("Order not found with id: " + orderId);
                });
    }

    /**
     * Helper method to authorize if the restaurant admin principal can manage the given order.
     * @param order The order to check against.
     * @param restaurantAdminPrincipal The principal of the restaurant admin.
     * @throws ResourceNotFoundException if related restaurant or user not found.
     * @throws AccessDeniedException if the user is not authorized.
     */
    private void authorizeRestaurantAdminForOrder(Order order, UserDetails restaurantAdminPrincipal) {
        Long orderRestaurantId = order.getRestaurantId();
        if (orderRestaurantId == null) {
            LOGGER.error("Authorization failed: Order ID {} is not associated with any restaurant.", order.getId());
            throw new IllegalStateException("Order " + order.getId() + " has no associated restaurant ID.");
        }

        String principalUsername = restaurantAdminPrincipal.getUsername();
        User adminUser = userRepository.findByUsername(principalUsername)
                .orElseThrow(() -> {
                    LOGGER.warn("Authorization failed: User {} (from principal) not found in repository.", principalUsername);
                    return new UsernameNotFoundException("Authenticated user " + principalUsername + " not found in database.");
                });

        Restaurant restaurant = restaurantRepository.findById(orderRestaurantId)
                .orElseThrow(() -> {
                    LOGGER.warn("Restaurant ID {} (for order ID {}) not found during authorization.", orderRestaurantId, order.getId());
                    return new ResourceNotFoundException("Restaurant not found with ID: " + orderRestaurantId + " for order " + order.getId());
                });

        boolean isAuthorized = restaurant.getRestaurantAdmins().stream()
                .anyMatch(admin -> admin.getId().equals(adminUser.getId()));

        if (!isAuthorized) {
            LOGGER.warn("Authorization failed: User {} (ID: {}) is not an admin for restaurant ID {} (Order ID: {})",
                    principalUsername, adminUser.getId(), orderRestaurantId, order.getId());
            throw new AccessDeniedException("User " + principalUsername +
                    " is not authorized to manage orders for restaurant ID " + orderRestaurantId);
        }
        LOGGER.debug("User {} is authorized admin for restaurant ID {} of order ID {}",
                principalUsername, orderRestaurantId, order.getId());
    }
}
