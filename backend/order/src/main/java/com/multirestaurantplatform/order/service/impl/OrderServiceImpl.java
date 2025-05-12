// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/impl/OrderServiceImpl.java
package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderItem;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.order.service.CartService; // Import CartService
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role; // Import Role
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CartService cartService; // Inject CartService

    @Override
    @Transactional
    public Order placeOrderFromCart(String userIdFromPath, UserDetails principal) {
        LOGGER.info("Attempting to place order from cart for user ID path: {} by principal: {}", userIdFromPath, principal.getUsername());

        // 1. Authorize the action
        User customer = userRepository.findByUsername(userIdFromPath) // Assuming userIdFromPath is the username
                .orElseThrow(() -> new ResourceNotFoundException("User with username " + userIdFromPath + " not found."));

        boolean isAdmin = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_" + Role.ADMIN.name()));

        if (!isAdmin && !principal.getUsername().equals(customer.getUsername())) {
            LOGGER.warn("Authorization failed: Principal {} is not authorized to place order for user {}", principal.getUsername(), customer.getUsername());
            throw new AccessDeniedException("You are not authorized to place an order for this user.");
        }
        LOGGER.info("User {} authorized to place order for user ID path: {}", principal.getUsername(), userIdFromPath);

        // 2. Retrieve the cart
        // The userId for cartService should be the actual username/identifier used by CartService
        CartResponse cartResponse = cartService.getCart(customer.getUsername()); // Use customer's username

        // 3. Validate the cart
        if (cartResponse == null || CollectionUtils.isEmpty(cartResponse.getItems())) {
            LOGGER.warn("Order placement failed: Cart for user {} is empty or not found.", customer.getUsername());
            throw new IllegalOrderStateException("Cannot place order: Cart is empty.");
        }
        if (cartResponse.getRestaurantId() == null) {
            LOGGER.warn("Order placement failed: Cart for user {} does not have a restaurant associated.", customer.getUsername());
            throw new IllegalOrderStateException("Cannot place order: Cart is not associated with a restaurant.");
        }

        // 4. Create new Order entity
        Order newOrder = new Order();
        newOrder.setCustomerId(customer.getId());
        newOrder.setRestaurantId(cartResponse.getRestaurantId());
        newOrder.setTotalPrice(cartResponse.getCartTotalPrice() != null ? cartResponse.getCartTotalPrice() : BigDecimal.ZERO);
        newOrder.setStatus(OrderStatus.PLACED);
        // newOrder.setPlacedAt(LocalDateTime.now()); // This is handled by setStatus
        // Delivery details can be set here if provided, or updated later. For now, they remain null.
        // newOrder.setDeliveryAddressLine1(...);

        // 5. Create OrderItem entities from cart items
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItemResponse cartItemDto : cartResponse.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItemId(cartItemDto.getMenuItemId());
            orderItem.setMenuItemName(cartItemDto.getMenuItemName());
            orderItem.setQuantity(cartItemDto.getQuantity());
            orderItem.setUnitPrice(cartItemDto.getUnitPrice());
            orderItem.setItemTotalPrice(cartItemDto.getTotalPrice());
            // orderItem.setSelectedOptions(...); // If you have options
            newOrder.addOrderItem(orderItem); // This also sets orderItem.setOrder(newOrder)
        }
        // newOrder.setOrderItems(orderItems); // addOrderItem handles this

        // 6. Save the order (OrderItems will be cascaded)
        Order savedOrder = orderRepository.save(newOrder);
        LOGGER.info("Order ID: {} placed successfully for user: {} by principal: {}", savedOrder.getId(), customer.getUsername(), principal.getUsername());

        // 7. Clear the cart
        try {
            cartService.clearCart(customer.getUsername());
            LOGGER.info("Cart cleared for user: {} after order placement.", customer.getUsername());
        } catch (Exception e) {
            // Log the error but don't let it fail the order placement transaction.
            // Cart clearing is a secondary concern here.
            LOGGER.error("Failed to clear cart for user {} after order placement. Order ID: {}. Error: {}",
                    customer.getUsername(), savedOrder.getId(), e.getMessage());
        }

        return savedOrder;
    }


    // ... other existing service methods (confirmOrder, markAsPreparing, etc.)
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
        authorizeRestaurantAdminForOrder(order, principal);
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
        authorizeRestaurantAdminForOrder(order, principal);
        validateOrderStatus(order, OrderStatus.OUT_FOR_DELIVERY, "complete delivery");
        order.setStatus(OrderStatus.DELIVERED);
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
