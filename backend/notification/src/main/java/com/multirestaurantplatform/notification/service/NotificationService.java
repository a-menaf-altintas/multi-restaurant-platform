// File: backend/notification/src/main/java/com/multirestaurantplatform/notification/service/NotificationService.java
package com.multirestaurantplatform.notification.service;

import java.math.BigDecimal;

/**
 * Service for sending notifications to users and restaurants.
 * Currently supports payment-related notifications.
 */
public interface NotificationService {
    
    /**
     * Sends a payment success notification to a customer.
     *
     * @param orderId The ID of the order that was paid for
     * @param customerEmail The email of the customer to notify
     * @param amount The amount that was paid
     * @param currency The currency of the payment
     */
    void sendPaymentSuccessToCustomer(Long orderId, String customerEmail, BigDecimal amount, String currency);
    
    /**
     * Notifies a restaurant of a new paid order.
     *
     * @param orderId The ID of the new order
     * @param restaurantId The ID of the restaurant
     * @param customerEmail The email of the customer (for contact if needed)
     * @param totalAmount The total amount of the order
     */
    void notifyRestaurantOfNewPaidOrder(Long orderId, Long restaurantId, String customerEmail, BigDecimal totalAmount);
    
    /**
     * Sends a payment failure notification to a customer.
     *
     * @param orderId The ID of the order
     * @param customerEmail The email of the customer to notify
     * @param failureReason The reason the payment failed
     */
    void sendPaymentFailureToCustomer(Long orderId, String customerEmail, String failureReason);
}