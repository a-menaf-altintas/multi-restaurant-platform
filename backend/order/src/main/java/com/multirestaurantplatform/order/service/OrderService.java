// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/OrderService.java
package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.dto.OrderStatisticsResponseDto;
import com.multirestaurantplatform.order.dto.PlaceOrderRequestDto; // Added
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

    Order placeOrderFromCart(String userId, UserDetails principal, PlaceOrderRequestDto placeOrderRequestDto); // Modified signature

    Order confirmOrder(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsPreparing(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsReadyForPickup(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsPickedUp(Long orderId, UserDetails restaurantAdminPrincipal);
    Order markAsOutForDelivery(Long orderId, UserDetails principal);
    Order completeDelivery(Long orderId, UserDetails principal);

    List<Order> findOrdersByCustomerId(Long customerId);
    Page<Order> findOrdersByCustomerId(Long customerId, Pageable pageable);
    Page<Order> findFilteredOrdersByCustomerId(
            Long customerId,
            OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    OrderStatisticsResponseDto getOrderStatisticsForCustomer(Long customerId);

    Order processPaymentSuccess(Long orderId, String paymentIntentId);
    Order processPaymentFailure(Long orderId, String paymentIntentId, String failureReason);
}
