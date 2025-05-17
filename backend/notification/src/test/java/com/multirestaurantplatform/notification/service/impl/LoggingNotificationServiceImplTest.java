// File: backend/notification/src/test/java/com/multirestaurantplatform/notification/service/impl/LoggingNotificationServiceImplTest.java
package com.multirestaurantplatform.notification.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingNotificationServiceImplTest {

    private LoggingNotificationServiceImpl notificationService;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        notificationService = new LoggingNotificationServiceImpl();
        
        // Get Logback Logger and attach a ListAppender to capture log messages
        logger = (Logger) LoggerFactory.getLogger(LoggingNotificationServiceImpl.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void sendPaymentSuccessToCustomer_ShouldLogCorrectMessage() {
        // Arrange
        Long orderId = 123L;
        String customerEmail = "test@example.com";
        BigDecimal amount = new BigDecimal("25.99");
        String currency = "USD";

        // Act
        notificationService.sendPaymentSuccessToCustomer(orderId, customerEmail, amount, currency);

        // Assert
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());
        
        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains("NOTIFICATION TO CUSTOMER: Payment Success"));
        assertTrue(logEvent.getFormattedMessage().contains(orderId.toString()));
        assertTrue(logEvent.getFormattedMessage().contains(customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains(amount.toString()));
        assertTrue(logEvent.getFormattedMessage().contains(currency));
    }

    @Test
    void notifyRestaurantOfNewPaidOrder_ShouldLogCorrectMessage() {
        // Arrange
        Long orderId = 123L;
        Long restaurantId = 456L;
        String customerEmail = "test@example.com";
        BigDecimal totalAmount = new BigDecimal("75.50");

        // Act
        notificationService.notifyRestaurantOfNewPaidOrder(orderId, restaurantId, customerEmail, totalAmount);

        // Assert
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());
        
        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains("NOTIFICATION TO RESTAURANT: New Paid Order"));
        assertTrue(logEvent.getFormattedMessage().contains(orderId.toString()));
        assertTrue(logEvent.getFormattedMessage().contains(restaurantId.toString()));
        assertTrue(logEvent.getFormattedMessage().contains(customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains(totalAmount.toString()));
    }

    @Test
    void sendPaymentFailureToCustomer_ShouldLogCorrectMessage() {
        // Arrange
        Long orderId = 123L;
        String customerEmail = "test@example.com";
        String failureReason = "Insufficient funds";

        // Act
        notificationService.sendPaymentFailureToCustomer(orderId, customerEmail, failureReason);

        // Assert
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());
        
        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains("NOTIFICATION TO CUSTOMER: Payment Failure"));
        assertTrue(logEvent.getFormattedMessage().contains(orderId.toString()));
        assertTrue(logEvent.getFormattedMessage().contains(customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains(failureReason));
    }
}