// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/OrderService.java
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
     */
    Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks a confirmed order as being prepared by the restaurant.
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as preparing.
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin,
     * used for authorization checks.
     * @return The updated Order entity with status PREPARING.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the order is not in CONFIRMED state.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to update this order.
     */
    Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks a preparing order as ready for pickup by the customer.
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as ready for pickup.
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin,
     * used for authorization checks.
     * @return The updated Order entity with status READY_FOR_PICKUP.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the order is not in PREPARING state.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to update this order.
     */
    Order markAsReadyForPickup(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks an order that is ready for pickup as delivered (picked up by customer).
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as picked up (delivered).
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin,
     * used for authorization checks.
     * @return The updated Order entity with status DELIVERED.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the order is not in READY_FOR_PICKUP state.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to update this order.
     */
    Order markAsPickedUp(Long orderId, UserDetails restaurantAdminPrincipal);


    // Future methods for order flow:
    // Order markAsOutForDelivery(Long orderId, UserDetails restaurantAdminPrincipal);
    // Order markAsDelivered(Long orderId, UserDetails principal); // Could be overloaded for delivery scenarios
    // Order cancelOrder(Long orderId, UserDetails principal); // User or admin
}
