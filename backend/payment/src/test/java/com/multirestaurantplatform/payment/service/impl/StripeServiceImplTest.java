// File: backend/payment/src/test/java/com/multirestaurantplatform/payment/service/impl/StripeServiceImplTest.java
package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.notification.service.NotificationService;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private NotificationService notificationService;

    private StripeServiceImpl stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeServiceImpl(orderService, notificationService);
        ReflectionTestUtils.setField(stripeService, "secretKey", "sk_test_123");
        ReflectionTestUtils.setField(stripeService, "webhookSecret", "whsec_123");
    }

    @Test
    void handlePaymentIntentSucceeded_shouldUpdateOrderAndSendNotifications() {
        // Arrange
        Long orderId = 123L;
        String paymentIntentId = "pi_123456";
        String customerEmail = "customer@example.com";
        BigDecimal amount = new BigDecimal("99.99");
        String currency = "USD";
        
        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        mockOrder.setRestaurantId(456L);
        mockOrder.setTotalPrice(amount);
        mockOrder.setStatus(OrderStatus.PLACED);
        
        when(orderService.processPaymentSuccess(orderId, paymentIntentId)).thenReturn(mockOrder);

        // Act
        stripeService.handlePaymentIntentSucceeded(orderId, paymentIntentId, customerEmail, amount, currency);

        // Assert
        verify(orderService, times(1)).processPaymentSuccess(orderId, paymentIntentId);
        
        // Verify customer notification was sent
        verify(notificationService, times(1)).sendPaymentSuccessToCustomer(
                eq(orderId),
                eq(customerEmail),
                eq(amount),
                eq(currency)
        );
        
        // Verify restaurant notification was sent
        verify(notificationService, times(1)).notifyRestaurantOfNewPaidOrder(
                eq(orderId),
                eq(456L), // Restaurant ID from the order
                eq(customerEmail),
                eq(amount) // Total price from the order
        );
    }

    @Test
    void handlePaymentIntentFailed_shouldUpdateOrderAndSendNotification() {
        // Arrange
        Long orderId = 123L;
        String paymentIntentId = "pi_123456";
        String customerEmail = "customer@example.com";
        String failureReason = "Card declined";

        // Act
        stripeService.handlePaymentIntentFailed(orderId, paymentIntentId, customerEmail, failureReason);

        // Assert
        verify(orderService, times(1)).processPaymentFailure(orderId, paymentIntentId, failureReason);
        
        // Verify customer notification about payment failure was sent
        verify(notificationService, times(1)).sendPaymentFailureToCustomer(
                eq(orderId),
                eq(customerEmail),
                eq(failureReason)
        );
    }
}