package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}