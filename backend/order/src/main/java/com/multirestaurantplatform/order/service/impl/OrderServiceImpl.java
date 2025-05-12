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

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            LOGGER.warn("Marking order as PREPARING failed: Order ID {} is not in CONFIRMED state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as preparing. Expected status CONFIRMED, but was " + order.getStatus() + "."
            );
        }

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

        if (order.getStatus() != OrderStatus.PREPARING) {
            LOGGER.warn("Marking order as READY_FOR_PICKUP failed: Order ID {} is not in PREPARING state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as ready for pickup. Expected status PREPARING, but was " + order.getStatus() + "."
            );
        }

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

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            LOGGER.warn("Marking order as DELIVERED (picked up) failed: Order ID {} is not in READY_FOR_PICKUP state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as picked up. Expected status READY_FOR_PICKUP, but was " + order.getStatus() + "."
            );
        }

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
        // For now, assume RESTAURANT_ADMIN can mark as out for delivery.
        // Later, this might involve a DELIVERY_PERSON role or different authorization.
        authorizeRestaurantAdminForOrder(order, principal);

        // An order should typically be READY_FOR_PICKUP before it goes OUT_FOR_DELIVERY.
        // Or, if you allow direct from PREPARING to OUT_FOR_DELIVERY, adjust this logic.
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP && order.getStatus() != OrderStatus.PREPARING) {
            LOGGER.warn("Marking order as OUT_FOR_DELIVERY failed: Order ID {} is not in READY_FOR_PICKUP or PREPARING state. Current state: {}", orderId, order.getStatus());
            throw new IllegalOrderStateException(
                    "Order cannot be marked as out for delivery. Expected status READY_FOR_PICKUP or PREPARING, but was " + order.getStatus() + "."
            );
        }
        // If moving from PREPARING, ensure readyAt is also set if not already.
        // The Order.setStatus() logic for OUT_FOR_DELIVERY handles setting readyAt if it's null.

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY); // This will set 'outForDeliveryAt' and potentially 'readyAt'
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as OUT_FOR_DELIVERY successfully by user: {}", savedOrder.getId(), principal.getUsername());
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
     * @param principal The principal of the user (expected to be a restaurant admin for now).
     * @throws ResourceNotFoundException if related restaurant or user not found.
     * @throws AccessDeniedException if the user is not authorized.
     */
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

        // Check if the user is an admin for this restaurant.
        // This logic assumes the principal is a RESTAURANT_ADMIN.
        // If other roles (like a global ADMIN or specific DELIVERY_PERSON) can perform this,
        // the authorization logic here or the calling method's @PreAuthorize would need adjustment.
        boolean isAuthorized = restaurant.getRestaurantAdmins().stream()
                .anyMatch(admin -> admin.getId().equals(adminUser.getId()));

        if (!isAuthorized) {
            // A global ADMIN might also be allowed to perform this.
            // Consider checking for ADMIN role here if that's a requirement.
            // For now, strict to restaurant admin.
            LOGGER.warn("Authorization failed: User {} (ID: {}) is not an admin for restaurant ID {} (Order ID: {})",
                    principalUsername, adminUser.getId(), orderRestaurantId, order.getId());
            throw new AccessDeniedException("User " + principalUsername +
                    " is not authorized to manage orders for restaurant ID " + orderRestaurantId);
        }
        LOGGER.debug("User {} is authorized for restaurant ID {} of order ID {}",
                principalUsername, orderRestaurantId, order.getId());
    }
}
