package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // You can add custom query methods here if needed in the future
    // For example:
    // List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);
    // List<Order> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);
}