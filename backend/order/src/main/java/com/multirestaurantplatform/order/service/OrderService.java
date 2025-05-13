// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/OrderService.java
package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.model.Order;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import com.multirestaurantplatform.order.model.OrderStatus;

import java.util.List;

public interface OrderService {

    /**
     * Creates a new order from the user's current shopping cart.
     * Sets the order status to PLACED and clears the cart.
     *
     * @param userId The ID of the user for whom the order is being placed.
     * This is typically the customer's ID.
     * @param principal The UserDetails of the authenticated user performing the action.
     * Used for authorization (e.g., customer placing their own order, or admin placing for a user).
     * @return The newly created Order entity.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the user or their cart is not found, or if cart items refer to non-existent entities.
     * @throws com.multirestaurantplatform.order.exception.IllegalOrderStateException if the cart is empty or in an invalid state for order placement.
     * @throws org.springframework.security.access.AccessDeniedException if the principal is not authorized to place an order for the given userId.
     * @throws com.multirestaurantplatform.order.exception.CartUpdateException if cart items are invalid (e.g. from MenuServiceClient).
     */
    Order placeOrderFromCart(String userId, UserDetails principal /*, PlaceOrderRequestDto placeOrderRequestDto if needed for delivery address etc. */);

    Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsReadyForPickup(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsPickedUp(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsOutForDelivery(Long orderId, UserDetails principal);
    Order completeDelivery(Long orderId, UserDetails principal);

    /**
     * Retrieves all orders for a specific customer.
     *
     * @param customerId The ID of the customer whose orders are being retrieved.
     * @return A list of Order entities for the specified customer.
     */
    List<Order> findOrdersByCustomerId(Long customerId);

    /**
     * Retrieves all orders for a specific customer with pagination and sorting.
     *
     * @param customerId The ID of the customer whose orders are being retrieved.
     * @param pageable The pagination and sorting information.
     * @return A page of Order entities for the specified customer.
     */
    Page<Order> findOrdersByCustomerId(Long customerId, Pageable pageable);

    /**
     * Retrieves filtered orders for a specific customer with pagination.
     *
     * @param customerId The ID of the customer
     * @param status Optional order status filter (can be null)
     * @param startDate Optional start date filter (can be null)
     * @param endDate Optional end date filter (can be null)
     * @param pageable Pagination and sorting information
     * @return A page of filtered orders
     */
    Page<Order> findFilteredOrdersByCustomerId(
            Long customerId,
            OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
}
