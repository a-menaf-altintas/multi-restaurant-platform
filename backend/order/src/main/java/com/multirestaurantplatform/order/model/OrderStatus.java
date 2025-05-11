package com.multirestaurantplatform.order.model;

public enum OrderStatus {
    PENDING_PAYMENT, // Optional: If payment is deferred
    PLACED,          // Initial status after successful order creation and payment
    CONFIRMED,       // Restaurant confirms they have received and accepted the order
    PREPARING,       // Restaurant is preparing the order
    READY_FOR_PICKUP,// Order is ready for pickup (if applicable)
    OUT_FOR_DELIVERY, // Order is out for delivery (if applicable)
    DELIVERED,       // Order has been successfully delivered
    CANCELLED_BY_USER,
    CANCELLED_BY_RESTAURANT,
    FAILED           // If order processing failed for some reason (e.g., payment failed post-placement)
}