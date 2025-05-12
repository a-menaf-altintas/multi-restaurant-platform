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
        validateOrderStatus(order, OrderStatus.PLACED, "confirm");
        order.setStatus(OrderStatus.CONFIRMED);
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
        validateOrderStatus(order, OrderStatus.CONFIRMED, "mark as preparing");
        order.setStatus(OrderStatus.PREPARING);
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
        validateOrderStatus(order, OrderStatus.PREPARING, "mark as ready for pickup");
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as READY_FOR_PICKUP successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order markAsPickedUp(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to mark order as DELIVERED (picked up) with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());
        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, restaurantAdminPrincipal);
        validateOrderStatus(order, OrderStatus.READY_FOR_PICKUP, "mark as picked up");
        order.setStatus(OrderStatus.DELIVERED);
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as DELIVERED (picked up) successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order markAsOutForDelivery(Long orderId, UserDetails principal) {
        LOGGER.info("Attempting to mark order as OUT_FOR_DELIVERY with ID: {} by user: {}", orderId, principal.getUsername());
        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, principal); // Assuming admin confirms this for now
        // An order can go out for delivery if it's ready for pickup or even directly from preparing
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP && order.getStatus() != OrderStatus.PREPARING) {
            LOGGER.warn("Marking order as OUT_FOR_DELIVERY failed: Order ID {} is not in READY_FOR_PICKUP or PREPARING state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as out for delivery. Expected status READY_FOR_PICKUP or PREPARING, but was " + order.getStatus() + "."
            );
        }
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as OUT_FOR_DELIVERY successfully by user: {}", savedOrder.getId(), principal.getUsername());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order completeDelivery(Long orderId, UserDetails principal) {
        LOGGER.info("Attempting to complete delivery for order ID: {} by user: {}", orderId, principal.getUsername());
        Order order = findOrderByIdOrThrow(orderId);
        // For now, assume RESTAURANT_ADMIN can confirm delivery based on driver feedback.
        // This authorization can be refined later if a DELIVERY_PERSON role is introduced.
        authorizeRestaurantAdminForOrder(order, principal);
        validateOrderStatus(order, OrderStatus.OUT_FOR_DELIVERY, "complete delivery");
        order.setStatus(OrderStatus.DELIVERED); // This will set 'deliveredAt'
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as DELIVERED (delivery completed) successfully by user: {}", savedOrder.getId(), principal.getUsername());
        return savedOrder;
    }

    private Order findOrderByIdOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    LOGGER.warn("Order operation failed: Order not found with ID: {}", orderId);
                    return new ResourceNotFoundException("Order not found with id: " + orderId);
                });
    }

    private void authorizeRestaurantAdminForOrder(Order order, UserDetails principal) {
        Long orderRestaurantId = order.getRestaurantId();
        if (orderRestaurantId == null) {
            LOGGER.error("Authorization failed: Order ID {} is not associated with any restaurant.", order.getId());
            throw new IllegalStateException("Order " + order.getId() + " has no associated restaurant ID.");
        }
        String principalUsername = principal.getUsername();
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
        LOGGER.debug("User {} is authorized for restaurant ID {} of order ID {}",
                principalUsername, orderRestaurantId, order.getId());
    }

    /**
     * Helper method to validate the current status of an order before a transition.
     * @param order The order to check.
     * @param expectedStatus The status the order should be in.
     * @param actionDescription A description of the action being attempted (e.g., "confirm", "mark as preparing").
     * @throws IllegalOrderStateException if the order's current status does not match the expected status.
     */
    private void validateOrderStatus(Order order, OrderStatus expectedStatus, String actionDescription) {
        if (order.getStatus() != expectedStatus) {
            String errorMessage = String.format(
                    "Order cannot be %s. Expected status %s, but was %s.",
                    actionDescription, expectedStatus, order.getStatus()
            );
            LOGGER.warn("Order state validation failed for order ID {}: {}", order.getId(), errorMessage);
            throw new IllegalOrderStateException(errorMessage);
        }
    }
}
