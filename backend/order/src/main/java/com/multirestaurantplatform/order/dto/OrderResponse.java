package com.multirestaurantplatform.order.dto;

import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus; // Ensure correct import from your existing enum
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private Long customerId;
    private Long restaurantId;
    private OrderStatus status;
    private BigDecimal totalPrice;
    private String deliveryAddressLine1;
    private String deliveryCity;
    private String deliveryPostalCode;
    private String customerContactNumber;
    private String specialInstructions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime placedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private List<OrderItemResponse> orderItems; // You'll need an OrderItemResponse DTO too

    // Static factory method to convert Order entity to OrderResponse DTO
    public static OrderResponse fromEntity(Order order) {
        if (order == null) {
            return null;
        }
        List<OrderItemResponse> itemResponses = order.getOrderItems() != null ?
                order.getOrderItems().stream().map(OrderItemResponse::fromEntity).collect(Collectors.toList()) :
                List.of();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getDeliveryAddressLine1(),
                order.getDeliveryCity(),
                order.getDeliveryPostalCode(),
                order.getCustomerContactNumber(),
                order.getSpecialInstructions(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getPlacedAt(),
                order.getConfirmedAt(),
                order.getPreparingAt(),
                order.getReadyAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                itemResponses
        );
    }
}