package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.OrderStatisticsResponseDto;
import com.multirestaurantplatform.order.exception.IllegalOrderStateException;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderItem;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.repository.OrderRepository;
import com.multirestaurantplatform.order.service.CartService;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CartService cartService;

    @Override
    @Transactional
    public Order placeOrderFromCart(String userIdFromPath, UserDetails principal) {
        LOGGER.info("Attempting to place order from cart for user ID path: {} by principal: {}", userIdFromPath, principal.getUsername());

        User customer = userRepository.findByUsername(userIdFromPath)
                .orElseThrow(() -> new ResourceNotFoundException("User with username " + userIdFromPath + " not found."));

        boolean isAdmin = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_" + Role.ADMIN.name()));

        if (!isAdmin && !principal.getUsername().equals(customer.getUsername())) {
            LOGGER.warn("Authorization failed: Principal {} is not authorized to place order for user {}", principal.getUsername(), customer.getUsername());
            throw new AccessDeniedException("You are not authorized to place an order for this user.");
        }
        LOGGER.info("User {} authorized to place order for user ID path: {}", principal.getUsername(), userIdFromPath);

        CartResponse cartResponse = cartService.getCart(customer.getUsername());

        if (cartResponse == null || CollectionUtils.isEmpty(cartResponse.getItems())) {
            LOGGER.warn("Order placement failed: Cart for user {} is empty or not found.", customer.getUsername());
            throw new IllegalOrderStateException("Cannot place order: Cart is empty.");
        }
        if (cartResponse.getRestaurantId() == null) {
            LOGGER.warn("Order placement failed: Cart for user {} does not have a restaurant associated.", customer.getUsername());
            throw new IllegalOrderStateException("Cannot place order: Cart is not associated with a restaurant.");
        }

        Order newOrder = new Order();
        newOrder.setCustomerId(customer.getId());
        newOrder.setRestaurantId(cartResponse.getRestaurantId());
        newOrder.setTotalPrice(cartResponse.getCartTotalPrice() != null ? cartResponse.getCartTotalPrice() : BigDecimal.ZERO);

        // **Set initial status to PENDING_PAYMENT**
        newOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        // Delivery details might be set here or when payment is confirmed

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItemResponse cartItemDto : cartResponse.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItemId(cartItemDto.getMenuItemId());
            orderItem.setMenuItemName(cartItemDto.getMenuItemName());
            orderItem.setQuantity(cartItemDto.getQuantity());
            orderItem.setUnitPrice(cartItemDto.getUnitPrice());
            orderItem.setItemTotalPrice(cartItemDto.getTotalPrice());
            newOrder.addOrderItem(orderItem);
        }

        Order savedOrder = orderRepository.save(newOrder);
        LOGGER.info("Order ID: {} created with status PENDING_PAYMENT for user: {} by principal: {}", savedOrder.getId(), customer.getUsername(), principal.getUsername());

        // Consider moving cart clearing to after successful payment processing via webhook
        try {
            cartService.clearCart(customer.getUsername());
            LOGGER.info("Cart cleared for user: {} after order creation (status PENDING_PAYMENT).", customer.getUsername());
        } catch (Exception e) {
            LOGGER.error("Failed to clear cart for user {} after order creation. Order ID: {}. Error: {}",
                    customer.getUsername(), savedOrder.getId(), e.getMessage());
        }

        return savedOrder;
    }

    @Override
    @Transactional
    public Order processPaymentSuccess(Long orderId, String paymentIntentId) {
        LOGGER.info("Processing payment success for order ID: {}, PaymentIntent ID: {}", orderId, paymentIntentId);
        Order order = findOrderByIdOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            LOGGER.warn("Order ID: {} is not in PENDING_PAYMENT status. Current status: {}. PaymentIntent ID: {}",
                    orderId, order.getStatus(), paymentIntentId);
            // Idempotency check: if already processed for this payment intent
            if (order.getStatus() == OrderStatus.PLACED && paymentIntentId.equals(order.getPaymentIntentId())) {
                LOGGER.info("Order ID: {} already marked as PLACED for PaymentIntent ID: {}. Skipping update.", orderId, paymentIntentId);
                return order;
            }
            // Optionally, throw an exception if it's an unexpected state transition
            // throw new IllegalOrderStateException("Order " + orderId + " cannot process payment success, status is " + order.getStatus());
        }

        order.setStatus(OrderStatus.PLACED); // Transition to PLACED upon successful payment
        order.setPaymentIntentId(paymentIntentId);
        order.setPaymentStatusDetail("Payment successful.");
        // The setStatus(OrderStatus.PLACED) call in Order.java should handle setting placedAt

        Order updatedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} status updated to {} after successful payment. PaymentIntent ID: {}",
                updatedOrder.getId(), updatedOrder.getStatus(), paymentIntentId);

        // TODO: Trigger further actions like notifying the restaurant.

        return updatedOrder;
    }

    @Override
    @Transactional
    public Order processPaymentFailure(Long orderId, String paymentIntentId, String failureReason) {
        LOGGER.info("Processing payment failure for order ID: {}. PaymentIntent ID: {}, Reason: {}",
                orderId, paymentIntentId, failureReason);
        Order order = findOrderByIdOrThrow(orderId);

        order.setStatus(OrderStatus.FAILED);
        if (paymentIntentId != null) {
            order.setPaymentIntentId(paymentIntentId);
        }
        order.setPaymentStatusDetail("Payment failed: " + (failureReason != null ? failureReason : "Unknown reason."));
        // The setStatus(OrderStatus.FAILED) call in Order.java might handle specific timestamps if configured

        Order updatedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} status updated to FAILED after payment failure. PaymentIntent ID: {}, Reason: {}",
                updatedOrder.getId(), paymentIntentId, failureReason);

        // TODO: Notify customer about payment failure.

        return updatedOrder;
    }


    @Override
    @Transactional
    public Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal) {
        LOGGER.info("Attempting to confirm order with ID: {} by user: {}", orderId, restaurantAdminPrincipal.getUsername());
        Order order = findOrderByIdOrThrow(orderId);
        authorizeRestaurantAdminForOrder(order, restaurantAdminPrincipal);

        // **Order must be PLACED (i.e., payment successful) before it can be CONFIRMED by the restaurant**
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalOrderStateException("Order " + orderId + " cannot be confirmed. Expected status PLACED (payment successful), but was " + order.getStatus() + ".");
        }
        // validateOrderStatus(order, OrderStatus.PLACED, "confirm"); // This helper can still be used if preferred

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
        // Order must be CONFIRMED before PREPARING
        validateOrderStatus(order, OrderStatus.CONFIRMED, "mark as preparing");
        order.setStatus(OrderStatus.PREPARING);
        Order savedOrder = orderRepository.save(order);
        LOGGER.info("Order ID: {} marked as PREPARING successfully by user: {}", savedOrder.getId(), restaurantAdminPrincipal.getUsername());
        return savedOrder;
    }

    // ... (markAsReadyForPickup, markAsPickedUp, markAsOutForDelivery, completeDelivery remain largely the same,
    // but ensure their prerequisite statuses align with the new flow, e.g., PREPARING -> READY_FOR_PICKUP) ...

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
            // If it can go from PREPARING directly to OUT_FOR_DELIVERY, this logic is fine.
            // Otherwise, it must be READY_FOR_PICKUP.
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

    @Override
    @Transactional(readOnly = true)
    public List<Order> findOrdersByCustomerId(Long customerId) {
        LOGGER.info("Retrieving orders for customer ID: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findOrdersByCustomerId(Long customerId, Pageable pageable) {
        LOGGER.info("Retrieving paginated orders for customer ID: {}, page: {}, size: {}",
                customerId, pageable.getPageNumber(), pageable.getPageSize());
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findFilteredOrdersByCustomerId(
            Long customerId,
            OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        LOGGER.info("Retrieving filtered orders for customer ID: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                customerId, status, startDate, endDate, pageable.getPageNumber(), pageable.getPageSize());

        LocalDateTime effectiveStartDate = startDate != null ? startDate : LocalDateTime.MIN;
        // For endDate, if null, it usually means up to the current moment or unbounded.
        // Using LocalDateTime.now() might prematurely filter out future-dated orders if that's ever a concept.
        // For historical queries, LocalDateTime.MAX or a very far future date is safer if endDate is null.
        // However, for this specific method, using LocalDateTime.now() is fine as it implies "up to now".
        LocalDateTime effectiveEndDate = endDate != null ? endDate : LocalDateTime.now();


        if (status != null && startDate != null && endDate != null) {
            return orderRepository.findByCustomerIdAndStatusAndCreatedAtBetween(
                    customerId, status, effectiveStartDate, effectiveEndDate, pageable);
        } else if (status != null) {
            return orderRepository.findByCustomerIdAndStatus(
                    customerId, status, pageable);
        } else if (startDate != null && endDate != null) {
            return orderRepository.findByCustomerIdAndCreatedAtBetween(
                    customerId, effectiveStartDate, effectiveEndDate, pageable);
        } else {
            // Default to finding all orders for the customer if no specific filters are applied beyond customerId
            return orderRepository.findByCustomerId(customerId, pageable);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatisticsResponseDto getOrderStatisticsForCustomer(Long customerId) {
        LOGGER.info("Generating order statistics for customer ID: {}", customerId);
        OrderStatisticsResponseDto stats = new OrderStatisticsResponseDto();
        Long totalOrders = orderRepository.countByCustomerId(customerId);
        stats.setTotalOrders(totalOrders);

        if (totalOrders == 0) {
            stats.setTotalSpent(BigDecimal.ZERO);
            stats.setOrdersByStatus(Map.of());
            stats.setAverageOrderAmount(BigDecimal.ZERO);
            stats.setRestaurantCount(0L);
            return stats;
        }

        BigDecimal totalSpent = orderRepository.sumTotalPriceByCustomerId(customerId);
        stats.setTotalSpent(totalSpent != null ? totalSpent : BigDecimal.ZERO);

        if (totalOrders > 0 && totalSpent != null && totalSpent.compareTo(BigDecimal.ZERO) != 0) {
            stats.setAverageOrderAmount(totalSpent.divide(new BigDecimal(totalOrders), 2, RoundingMode.HALF_UP));
        } else {
            stats.setAverageOrderAmount(BigDecimal.ZERO);
        }

        List<Object[]> statusCounts = orderRepository.countOrdersByStatusForCustomer(customerId);
        Map<String, Long> ordersByStatus = new HashMap<>();
        for (Object[] result : statusCounts) {
            OrderStatus currentStatus = (OrderStatus) result[0]; // Renamed to avoid conflict
            Long count = (Long) result[1];
            ordersByStatus.put(currentStatus.name(), count);
        }
        stats.setOrdersByStatus(ordersByStatus);

        stats.setFirstOrderDate(orderRepository.findFirstOrderDateByCustomerId(customerId));
        stats.setLastOrderDate(orderRepository.findLastOrderDateByCustomerId(customerId));
        stats.setRestaurantCount(orderRepository.countDistinctRestaurantsByCustomerId(customerId));

        List<Object[]> mostOrderedRestaurantData = orderRepository.findMostOrderedRestaurantByCustomerId(customerId); // Renamed
        if (!mostOrderedRestaurantData.isEmpty()) {
            Object[] result = mostOrderedRestaurantData.get(0); // Use get(0) as it's ordered
            Long restaurantId = (Long) result[0];
            Long orderCount = (Long) result[1]; // This is the count of orders for that restaurant

            stats.setMostOrderedRestaurantId(restaurantId);
            stats.setMostOrderedRestaurantOrderCount(orderCount); // This should be the order count from query

            restaurantRepository.findById(restaurantId).ifPresent(restaurant -> {
                stats.setMostOrderedRestaurantName(restaurant.getName());
            });
        }
        return stats;
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

        // Check if the user is a RESTAURANT_ADMIN for the order's restaurant
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