// File: backend/payment/src/test/java/com/multirestaurantplatform/payment/service/impl/StripeServiceImplIntegrationTest.java
package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.notification.service.NotificationService;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceImplIntegrationTest {

    private StripeServiceImpl stripeService;

    @Mock
    private OrderService orderService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Order mockOrder;

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    @BeforeEach
    void setUp() {
        stripeService = new StripeServiceImpl(orderService, notificationService);
        ReflectionTestUtils.setField(stripeService, "webhookSecret", WEBHOOK_SECRET);
    }

    @Test
    void processPaymentSuccess_ShouldSendNotifications() {
        // Given
        Long orderId = 123L;
        Long restaurantId = 456L;
        String customerEmail = "test@example.com";
        String paymentIntentId = "pi_test";
        BigDecimal orderTotal = new BigDecimal("25.99");
        BigDecimal stripeAmount = new BigDecimal("25.99");
        String currency = "usd";

        // Mock order response
        when(mockOrder.getRestaurantId()).thenReturn(restaurantId);
        when(mockOrder.getTotalPrice()).thenReturn(orderTotal);
        when(orderService.processPaymentSuccess(orderId, paymentIntentId)).thenReturn(mockOrder);

        // When - directly call the method
        stripeService.handlePaymentIntentSucceeded(orderId, paymentIntentId, customerEmail, stripeAmount, currency);

        // Then
        verify(orderService).processPaymentSuccess(eq(orderId), eq(paymentIntentId));
        verify(notificationService).notifyRestaurantOfNewPaidOrder(
                eq(orderId),
                eq(restaurantId),
                eq(customerEmail),
                eq(orderTotal)
        );
        verify(notificationService).sendPaymentSuccessToCustomer(
                eq(orderId),
                eq(customerEmail),
                eq(stripeAmount),
                eq(currency)
        );
    }

    @Test
    void processPaymentFailure_ShouldSendNotification() {
        // Given
        Long orderId = 123L;
        String customerEmail = "test@example.com";
        String paymentIntentId = "pi_test_failed";
        String failureReason = "Insufficient funds";

        // When - directly call the method
        stripeService.handlePaymentIntentFailed(orderId, paymentIntentId, customerEmail, failureReason);

        // Then
        verify(orderService).processPaymentFailure(eq(orderId), eq(paymentIntentId), eq(failureReason));
        verify(notificationService).sendPaymentFailureToCustomer(
                eq(orderId),
                eq(customerEmail),
                eq(failureReason)
        );
    }
}