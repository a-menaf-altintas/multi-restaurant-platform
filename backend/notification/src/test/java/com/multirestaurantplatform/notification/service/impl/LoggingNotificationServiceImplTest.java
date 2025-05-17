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

import static org.junit.jupiter.api.Assertions.*;

class LoggingNotificationServiceImplTest {

    private LoggingNotificationServiceImpl notificationService;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        notificationService = new LoggingNotificationServiceImpl();

        // Get the logger from the implementation class
        logger = (Logger) LoggerFactory.getLogger(LoggingNotificationServiceImpl.class);

        // Create and start a ListAppender to capture log messages
        listAppender = new ListAppender<>();
        listAppender.start();

        // Add the appender to the logger
        logger.addAppender(listAppender);
    }

    @Test
    void sendPaymentSuccessToCustomer_shouldLogCorrectInfo() {
        // Arrange
        Long orderId = 12345L;
        String customerEmail = "test@example.com";
        BigDecimal amount = new BigDecimal("99.99");
        String currency = "USD";

        // Act
        notificationService.sendPaymentSuccessToCustomer(orderId, customerEmail, amount, currency);

        // Assert
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());

        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains("NOTIFICATION TO CUSTOMER: Payment Success"));
        assertTrue(logEvent.getFormattedMessage().contains("Order ID: " + orderId));
        assertTrue(logEvent.getFormattedMessage().contains("Customer Email: " + customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains("Amount: " + amount + " " + currency));
    }

    @Test
    void notifyRestaurantOfNewPaidOrder_shouldLogCorrectInfo() {
        // Arrange
        Long orderId = 12345L;
        Long restaurantId = 789L;
        String customerEmail = "test@example.com";
        BigDecimal totalAmount = new BigDecimal("149.99");

        // Act
        notificationService.notifyRestaurantOfNewPaidOrder(orderId, restaurantId, customerEmail, totalAmount);

        // Assert
        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(1, logEvents.size());

        ILoggingEvent logEvent = logEvents.get(0);
        assertEquals(Level.INFO, logEvent.getLevel());
        assertTrue(logEvent.getFormattedMessage().contains("NOTIFICATION TO RESTAURANT: New Paid Order"));
        assertTrue(logEvent.getFormattedMessage().contains("Order ID: " + orderId));
        assertTrue(logEvent.getFormattedMessage().contains("Restaurant ID: " + restaurantId));
        assertTrue(logEvent.getFormattedMessage().contains("Customer Email: " + customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains("Total Amount: " + totalAmount));
    }

    @Test
    void sendPaymentFailureToCustomer_shouldLogCorrectInfo() {
        // Arrange
        Long orderId = 12345L;
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
        assertTrue(logEvent.getFormattedMessage().contains("Order ID: " + orderId));
        assertTrue(logEvent.getFormattedMessage().contains("Customer Email: " + customerEmail));
        assertTrue(logEvent.getFormattedMessage().contains("Reason: " + failureReason));
    }
}