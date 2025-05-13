package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

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
    /**
     * Count the total number of orders for a customer.
     *
     * @param customerId The customer ID
     * @return The total count of orders
     */
    Long countByCustomerId(Long customerId);

    /**
     * Find the sum of total prices for all orders of a customer.
     *
     * @param customerId The customer ID
     * @return The sum of all order total prices
     */
    @Query("SELECT SUM(o.totalPrice) FROM Order o WHERE o.customerId = :customerId")
    BigDecimal sumTotalPriceByCustomerId(@Param("customerId") Long customerId);

    /**
     * Count orders by status for a specific customer.
     *
     * @param customerId The customer ID
     * @return Map of status to count
     */
    @Query("SELECT o.status as status, COUNT(o) as count FROM Order o WHERE o.customerId = :customerId GROUP BY o.status")
    List<Object[]> countOrdersByStatusForCustomer(@Param("customerId") Long customerId);

    /**
     * Find the earliest order date for a customer.
     *
     * @param customerId The customer ID
     * @return The date of the first order
     */
    @Query("SELECT MIN(o.createdAt) FROM Order o WHERE o.customerId = :customerId")
    LocalDateTime findFirstOrderDateByCustomerId(@Param("customerId") Long customerId);

    /**
     * Find the latest order date for a customer.
     *
     * @param customerId The customer ID
     * @return The date of the last order
     */
    @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.customerId = :customerId")
    LocalDateTime findLastOrderDateByCustomerId(@Param("customerId") Long customerId);

    /**
     * Count distinct restaurants ordered from by a customer.
     *
     * @param customerId The customer ID
     * @return The count of unique restaurants
     */
    @Query("SELECT COUNT(DISTINCT o.restaurantId) FROM Order o WHERE o.customerId = :customerId")
    Long countDistinctRestaurantsByCustomerId(@Param("customerId") Long customerId);

    /**
     * Find the most frequently ordered from restaurant for a customer.
     *
     * @param customerId The customer ID
     * @return Object array with restaurantId, count
     */
    @Query("SELECT o.restaurantId, COUNT(o) as count FROM Order o WHERE o.customerId = :customerId GROUP BY o.restaurantId ORDER BY count DESC")
    List<Object[]> findMostOrderedRestaurantByCustomerId(@Param("customerId") Long customerId);
}