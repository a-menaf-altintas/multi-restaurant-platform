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
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin.
     * @return The updated Order entity with status CONFIRMED.
     */
    Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks a confirmed order as being prepared by the restaurant.
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as preparing.
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin.
     * @return The updated Order entity with status PREPARING.
     */
    Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks a preparing order as ready for pickup by the customer.
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as ready for pickup.
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin.
     * @return The updated Order entity with status READY_FOR_PICKUP.
     */
    Order markAsReadyForPickup(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks an order that is ready for pickup as delivered (picked up by customer).
     * This action is typically performed by a restaurant administrator.
     *
     * @param orderId The ID of the order to be marked as picked up (delivered).
     * @param restaurantAdminPrincipal The UserDetails of the authenticated restaurant admin.
     * @return The updated Order entity with status DELIVERED.
     */
    Order markAsPickedUp(Long orderId, UserDetails restaurantAdminPrincipal);

    /**
     * Marks an order that is ready (e.g. READY_FOR_PICKUP) as out for delivery.
     * This action is typically performed by a restaurant administrator or a delivery manager.
     *
     * @param orderId The ID of the order to be marked as out for delivery.
     * @param principal The UserDetails of the authenticated user performing the action.
     * @return The updated Order entity with status OUT_FOR_DELIVERY.
     */
    Order markAsOutForDelivery(Long orderId, UserDetails principal);

    /**
     * Marks an order that is out for delivery as delivered to the customer.
     * This action can be performed by a restaurant administrator or potentially a delivery person.
     *
     * @param orderId The ID of the order to be marked as delivered.
     * @param principal The UserDetails of the authenticated user performing the action.
     * @return The updated Order entity with status DELIVERED.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the order is not in OUT_FOR_DELIVERY state.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to update this order.
     */
    Order completeDelivery(Long orderId, UserDetails principal);


    // Future methods for order flow:
    // Order cancelOrder(Long orderId, UserDetails principal); // User or admin
}
