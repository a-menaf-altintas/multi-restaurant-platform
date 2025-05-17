// File: backend/notification/src/main/java/com/multirestaurantplatform/notification/service/impl/LoggingNotificationServiceImpl.java
package com.multirestaurantplatform.notification.service.impl;

import com.multirestaurantplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Implementation of NotificationService that logs notifications instead of actually sending them.
 * This is a stub implementation for development purposes.
 */
@Service
@RequiredArgsConstructor
public class LoggingNotificationServiceImpl implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingNotificationServiceImpl.class);
    
    @Override
    public void sendPaymentSuccessToCustomer(Long orderId, String customerEmail, BigDecimal amount, String currency) {
        logger.info("NOTIFICATION TO CUSTOMER: Payment Success for Order ID: {}, Customer Email: {}, Amount: {} {}", 
                orderId, customerEmail, amount, currency);
        
        // In a real implementation, this would send an email or other notification to the customer
        // Example: emailService.sendEmail(customerEmail, "Payment Success", "Your payment of " + amount + " " + currency + " for order #" + orderId + " was successful.");
    }
    
    @Override
    public void notifyRestaurantOfNewPaidOrder(Long orderId, Long restaurantId, String customerEmail, BigDecimal totalAmount) {
        logger.info("NOTIFICATION TO RESTAURANT: New Paid Order - Order ID: {}, Restaurant ID: {}, Customer Email: {}, Total Amount: {}", 
                orderId, restaurantId, customerEmail, totalAmount);
        
        // In a real implementation, this would send an email, SMS, or dashboard notification to the restaurant
        // Example: restaurantNotificationService.sendNewOrderAlert(restaurantId, "New Order #" + orderId + " received. Total: " + totalAmount);
    }
    
    @Override
    public void sendPaymentFailureToCustomer(Long orderId, String customerEmail, String failureReason) {
        logger.info("NOTIFICATION TO CUSTOMER: Payment Failure for Order ID: {}, Customer Email: {}, Reason: {}", 
                orderId, customerEmail, failureReason);
        
        // In a real implementation, this would send an email or other notification to the customer
        // Example: emailService.sendEmail(customerEmail, "Payment Failed", "Your payment for order #" + orderId + " failed. Reason: " + failureReason);
    }
}