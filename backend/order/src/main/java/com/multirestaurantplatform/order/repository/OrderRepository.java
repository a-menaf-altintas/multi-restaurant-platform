package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // You can add custom query methods here if needed in the future
    // For example:
    // List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);
    // List<Order> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    /**
     * Finds all orders for a specific customer.
     *
     * @param customerId The ID of the customer whose orders to find.
     * @return A list of orders for the specified customer.
     */
    List<Order> findByCustomerId(Long customerId);
    /**
     * Finds all orders for a specific customer with pagination and sorting.
     *
     * @param customerId The ID of the customer whose orders to find.
     * @param pageable The pagination and sorting information.
     * @return A page of orders for the specified customer.
     */
    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    /**
     * Finds orders for a customer with optional filtering by status and date range.
     *
     * @param customerId The ID of the customer
     * @param status Optional order status filter
     * @param startDate Optional start date filter (inclusive)
     * @param endDate Optional end date filter (inclusive)
     * @param pageable Pagination and sorting information
     * @return A page of filtered orders
     */
    Page<Order> findByCustomerIdAndStatusAndCreatedAtBetween(
            Long customerId,
            OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Finds orders for a customer with optional filtering by date range.
     *
     * @param customerId The ID of the customer
     * @param startDate Optional start date filter (inclusive)
     * @param endDate Optional end date filter (inclusive)
     * @param pageable Pagination and sorting information
     * @return A page of filtered orders
     */
    Page<Order> findByCustomerIdAndCreatedAtBetween(
            Long customerId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Finds orders for a customer with optional filtering by status.
     *
     * @param customerId The ID of the customer
     * @param status Order status filter
     * @param pageable Pagination and sorting information
     * @return A page of filtered orders
     */
    Page<Order> findByCustomerIdAndStatus(
            Long customerId,
            OrderStatus status,
            Pageable pageable);
}