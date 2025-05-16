// File: backend/order/src/main/java/com/multirestaurantplatform/order/dto/OrderResponse.java
package com.multirestaurantplatform.order.dto;

import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import lombok.AllArgsConstructor; // Keep or adjust based on constructor needs
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
// Remove @AllArgsConstructor or ensure it includes new fields, or use manual constructor
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
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private List<OrderItemResponse> orderItems;

    // --- New Fields ---
    private String paymentIntentId;
    private String paymentStatusDetail;
    // --------------------

    // Manual constructor updated to include new fields
    public OrderResponse(Long id, Long customerId, Long restaurantId, OrderStatus status, BigDecimal totalPrice,
                         String deliveryAddressLine1, String deliveryCity, String deliveryPostalCode,
                         String customerContactNumber, String specialInstructions, LocalDateTime createdAt,
                         LocalDateTime updatedAt, LocalDateTime placedAt, LocalDateTime confirmedAt,
                         LocalDateTime preparingAt, LocalDateTime readyAt, LocalDateTime outForDeliveryAt,
                         LocalDateTime deliveredAt, LocalDateTime cancelledAt, List<OrderItemResponse> orderItems,
                         String paymentIntentId, String paymentStatusDetail) { // Added new params
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.status = status;
        this.totalPrice = totalPrice;
        this.deliveryAddressLine1 = deliveryAddressLine1;
        this.deliveryCity = deliveryCity;
        this.deliveryPostalCode = deliveryPostalCode;
        this.customerContactNumber = customerContactNumber;
        this.specialInstructions = specialInstructions;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.placedAt = placedAt;
        this.confirmedAt = confirmedAt;
        this.preparingAt = preparingAt;
        this.readyAt = readyAt;
        this.outForDeliveryAt = outForDeliveryAt;
        this.deliveredAt = deliveredAt;
        this.cancelledAt = cancelledAt;
        this.orderItems = orderItems;
        this.paymentIntentId = paymentIntentId; // Assign new field
        this.paymentStatusDetail = paymentStatusDetail; // Assign new field
    }


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
                order.getOutForDeliveryAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                itemResponses,
                order.getPaymentIntentId(),         // Include new field
                order.getPaymentStatusDetail()      // Include new field
        );
    }
}