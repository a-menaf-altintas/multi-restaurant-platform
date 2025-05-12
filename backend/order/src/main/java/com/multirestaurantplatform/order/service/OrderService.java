package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.model.Order;
import org.springframework.security.core.userdetails.UserDetails; // For authorization

public interface OrderService {

    /**
     * Confirms an order placed by a customer.
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be confirmed.
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin,
     * used for authorization checks to ensure the admin
     * is associated with the order's restaurant.
     * @return The updated Order entity with status CONFIRMED.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the order is not in PLACED state.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to confirm this order.
     **/
    Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal);

    // Future methods for order flow:
    // Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal);
    // Order markAsReady(Long orderId, UserDetails restaurantAdminPrincipal);
    // Order markAsDelivered(Long orderId, UserDetails restaurantAdminPrincipal); // Or by a delivery agent
    // Order cancelOrder(Long orderId, UserDetails principal); // User or admin
}