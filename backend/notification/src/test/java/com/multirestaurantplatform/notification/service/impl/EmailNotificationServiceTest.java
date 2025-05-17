// File: backend/notification/src/test/java/com/multirestaurantplatform/notification/service/impl/EmailNotificationServiceTest.java
package com.multirestaurantplatform.notification.service.impl;

import com.multirestaurantplatform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test case for a future EmailNotificationService implementation.
 * This demonstrates how tests should be written for a real implementation
 * that sends emails instead of just logging.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private NotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        // EmailNotificationService would be a future implementation that actually sends emails
        // For now, we're demonstrating how to test it
        emailNotificationService = new EmailNotificationServiceMock(mailSender);
    }

    @Test
    void sendPaymentSuccessToCustomer_shouldSendEmail() {
        // Arrange
        Long orderId = 12345L;
        String customerEmail = "test@example.com";
        BigDecimal amount = new BigDecimal("99.99");
        String currency = "USD";

        // Act
        emailNotificationService.sendPaymentSuccessToCustomer(orderId, customerEmail, amount, currency);

        // Assert - verify that mailSender.send() was called with a SimpleMailMessage
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyRestaurantOfNewPaidOrder_shouldSendEmail() {
        // Arrange
        Long orderId = 12345L;
        Long restaurantId = 789L;
        String customerEmail = "test@example.com";
        BigDecimal totalAmount = new BigDecimal("149.99");

        // Act
        emailNotificationService.notifyRestaurantOfNewPaidOrder(orderId, restaurantId, customerEmail, totalAmount);

        // Assert - verify that mailSender.send() was called with a SimpleMailMessage
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPaymentFailureToCustomer_shouldSendEmail() {
        // Arrange
        Long orderId = 12345L;
        String customerEmail = "test@example.com";
        String failureReason = "Insufficient funds";

        // Act
        emailNotificationService.sendPaymentFailureToCustomer(orderId, customerEmail, failureReason);

        // Assert - verify that mailSender.send() was called with a SimpleMailMessage
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    /**
     * Mock implementation of a possible future EmailNotificationService.
     * This demonstrates how an email-based implementation might look.
     */
    private static class EmailNotificationServiceMock implements NotificationService {

        private final JavaMailSender mailSender;

        private EmailNotificationServiceMock(JavaMailSender mailSender) {
            this.mailSender = mailSender;
        }

        @Override
        public void sendPaymentSuccessToCustomer(Long orderId, String customerEmail, BigDecimal amount, String currency) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(customerEmail);
            message.setSubject("Payment Success for Order #" + orderId);
            message.setText("Your payment of " + amount + " " + currency + " for order #" + orderId + " was successful.");
            mailSender.send(message);
        }

        @Override
        public void notifyRestaurantOfNewPaidOrder(Long orderId, Long restaurantId, String customerEmail, BigDecimal totalAmount) {
            // In a real implementation, we would look up the restaurant's email
            String restaurantEmail = "restaurant" + restaurantId + "@example.com";
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(restaurantEmail);
            message.setSubject("New Order #" + orderId + " Received");
            message.setText("You have received a new order #" + orderId + " with total amount " 
                    + totalAmount + ". Customer email: " + customerEmail);
            mailSender.send(message);
        }

        @Override
        public void sendPaymentFailureToCustomer(Long orderId, String customerEmail, String failureReason) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(customerEmail);
            message.setSubject("Payment Failed for Order #" + orderId);
            message.setText("Your payment for order #" + orderId + " failed. Reason: " + failureReason);
            mailSender.send(message);
        }
    }
}