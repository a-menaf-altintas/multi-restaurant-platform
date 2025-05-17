// File: backend/payment/src/main/java/com/multirestaurantplatform/payment/service/impl/StripeServiceImpl.java
package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.notification.service.NotificationService;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.payment.service.StripeService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Implementation of StripeService that handles payment processing and webhooks
 * with Stripe integration. Also handles sending notifications for payment events.
 */
@Service
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeServiceImpl.class);

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final OrderService orderService;
    private final NotificationService notificationService;

    /**
     * Initializes the Stripe API with the configured secret key.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("StripeService initialized with API key. Webhook secret loaded: {}",
                webhookSecret != null && !webhookSecret.startsWith("whsec_YOUR_FALLBACK") ?
                        "[PRESENT]" : "[MISSING or FALLBACK]");
    }

    /**
     * Exception thrown when payment processing fails.
     */
    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
        public PaymentProcessingException(String message) {
            super(message);
        }
    }

    /**
     * Creates a PaymentIntent with Stripe.
     *
     * @param amount The amount for the payment in the smallest currency unit (e.g., cents).
     * @param currency The 3-letter ISO currency code (e.g., "usd", "cad").
     * @param orderId Your internal order ID, to be stored as metadata.
     * @param customerEmail The email of the customer, for Stripe's records and receipts.
     * @return The client secret of the created PaymentIntent.
     * @throws PaymentProcessingException if there's an error with Stripe or payment creation.
     */
    @Override
    public String createPaymentIntent(long amount, String currency, String orderId, String customerEmail)
            throws PaymentProcessingException {
        try {
            logger.info("Attempting to create PaymentIntent for orderId: {}, amount: {}, currency: {}, customerEmail: {}",
                    orderId, amount, currency, customerEmail);

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .putMetadata("order_id", orderId)
                    .putMetadata("customer_email_for_order", customerEmail)
                    .setReceiptEmail(customerEmail);

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            logger.info("PaymentIntent created successfully. Stripe PaymentIntent ID: {}, Order ID: {}, Client Secret: {}",
                    paymentIntent.getId(), orderId, paymentIntent.getClientSecret() != null ? "[PRESENT]" : "[MISSING]");

            if (paymentIntent.getClientSecret() == null) {
                logger.error("Critical: PaymentIntent {} for order {} was created but client_secret is null.",
                        paymentIntent.getId(), orderId);
                throw new PaymentProcessingException("Failed to create PaymentIntent for order " +
                        orderId + ": Client secret was null after creation.");
            }
            return paymentIntent.getClientSecret();

        } catch (StripeException e) {
            logger.error("Stripe API error creating PaymentIntent for orderId {}: {} (Status: {}, Code: {})",
                    orderId, e.getMessage(), e.getStatusCode(), e.getCode(), e);
            throw new PaymentProcessingException("Stripe API error while creating PaymentIntent for order " +
                    orderId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating PaymentIntent for orderId {}: {}",
                    orderId, e.getMessage(), e);
            throw new PaymentProcessingException("Unexpected error while creating PaymentIntent for order " +
                    orderId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Handles incoming Stripe webhook events.
     * Verifies the event signature and processes the event based on its type.
     *
     * @param payload The raw JSON payload from the webhook request.
     * @param sigHeader The value of the 'Stripe-Signature' header.
     * @throws SignatureVerificationException if the signature verification fails.
     * @throws PaymentProcessingException for other processing errors.
     */
    @Override
    public void handleWebhookEvent(String payload, String sigHeader)
            throws SignatureVerificationException, PaymentProcessingException {
        Event event;
        try {
            // Verify webhook signature using Stripe SDK
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Webhook error: Invalid Stripe signature. Message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Webhook error: Could not parse payload or other construction error. Message: {}",
                    e.getMessage());
            throw new PaymentProcessingException("Error constructing webhook event: " + e.getMessage(), e);
        }

        // Extract the Stripe object from the event
        StripeObject stripeObject = null;
        if (event.getDataObjectDeserializer().getObject().isPresent()) {
            stripeObject = event.getDataObjectDeserializer().getObject().get();
        } else {
            logger.warn("Webhook warning: Deserialization of event data object failed for event ID: {} Type: {}",
                    event.getId(), event.getType());
        }

        logger.info("Received Stripe Event: Id='{}', Type='{}', Livemode='{}'",
                event.getId(), event.getType(), event.getLivemode());

        // Process the event based on its type
        switch (event.getType()) {
            case "payment_intent.succeeded":
                handlePaymentIntentSucceeded(event, stripeObject);
                break;

            case "payment_intent.payment_failed":
                handlePaymentIntentFailed(event, stripeObject);
                break;

            default:
                logger.warn("Unhandled Stripe event type: {}", event.getType());
        }
    }

    /**
     * Processes a successful payment intent event.
     * Updates the order status and sends notifications.
     *
     * @param event The Stripe event
     * @param stripeObject The Stripe object (should be a PaymentIntent)
     */
    void handlePaymentIntentSucceeded(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof PaymentIntent)) {
            logger.warn("payment_intent.succeeded event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.",
                    event.getId());
            return;
        }

        PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
        String orderIdStr = paymentIntent.getMetadata().get("order_id");
        String paymentIntentId = paymentIntent.getId();

        // Get customer email - first from metadata, then fallback to receipt_email
        String customerEmail = paymentIntent.getMetadata().get("customer_email_for_order");
        if (customerEmail == null || customerEmail.isBlank()) {
            customerEmail = paymentIntent.getReceiptEmail();
        }

        logger.info("PaymentIntent Succeeded: PI_ID={}, Amount={}, OrderID (metadata)={}",
                paymentIntentId, paymentIntent.getAmount(), orderIdStr);

        if (orderIdStr == null) {
            logger.warn("PaymentIntent Succeeded (PI ID: {}) but order_id was missing from metadata.", paymentIntentId);
            return;
        }

        try {
            Long orderId = Long.parseLong(orderIdStr);

            // Convert Stripe's amount (in cents) to a BigDecimal for notification
            BigDecimal amount = BigDecimal.valueOf(paymentIntent.getAmount())
                    .divide(BigDecimal.valueOf(100));

            // Call the helper method with all the extracted data
            handlePaymentIntentSucceeded(orderId, paymentIntentId, customerEmail, amount, paymentIntent.getCurrency());

        } catch (NumberFormatException e) {
            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}",
                    orderIdStr, paymentIntentId, e);
        } catch (Exception e) {
            logger.error("Error during post-payment success processing for orderId: {}, PI ID: {}. Error: {}",
                    orderIdStr, paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Processes a failed payment intent event.
     * Updates the order status and sends notifications.
     *
     * @param event The Stripe event
     * @param stripeObject The Stripe object (should be a PaymentIntent)
     */
    void handlePaymentIntentFailed(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof PaymentIntent)) {
            logger.warn("payment_intent.payment_failed event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.",
                    event.getId());
            return;
        }

        PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
        String orderIdStr = paymentIntent.getMetadata().get("order_id");
        String paymentIntentId = paymentIntent.getId();

        // Get failure reason from the PaymentIntent
        String failureReason = paymentIntent.getLastPaymentError() != null ?
                paymentIntent.getLastPaymentError().getMessage() : "N/A";

        // Get customer email - first from metadata, then fallback to receipt_email
        String customerEmail = paymentIntent.getMetadata().get("customer_email_for_order");
        if (customerEmail == null || customerEmail.isBlank()) {
            customerEmail = paymentIntent.getReceiptEmail();
        }

        logger.info("PaymentIntent Failed: PI_ID={}, OrderID (metadata)={}, FailureReason={}",
                paymentIntentId, orderIdStr, failureReason);

        if (orderIdStr == null) {
            logger.warn("PaymentIntent Failed (PI ID: {}) but order_id was missing from metadata.", paymentIntentId);
            return;
        }

        try {
            Long orderId = Long.parseLong(orderIdStr);

            // Call the helper method with all the extracted data
            handlePaymentIntentFailed(orderId, paymentIntentId, customerEmail, failureReason);

        } catch (NumberFormatException e) {
            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}",
                    orderIdStr, paymentIntentId, e);
        } catch (Exception e) {
            logger.error("Error during post-payment failure processing for orderId: {}, PI ID: {}. Error: {}",
                    orderIdStr, paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Helper method for tests - handles a successful payment
     *
     * @param orderId The order ID
     * @param paymentIntentId The Stripe payment intent ID
     * @param customerEmail The customer's email
     * @param amount The payment amount
     * @param currency The payment currency
     */
    void handlePaymentIntentSucceeded(Long orderId, String paymentIntentId, String customerEmail,
                                      BigDecimal amount, String currency) {
        try {
            // Update order status through the OrderService
            Order order = orderService.processPaymentSuccess(orderId, paymentIntentId);
            logger.info("OrderService successfully processed payment success for orderId: {}", orderId);

            // Send notifications using the NotificationService
            notificationService.notifyRestaurantOfNewPaidOrder(
                    orderId,
                    order.getRestaurantId(),
                    customerEmail != null ? customerEmail : "N/A",
                    order.getTotalPrice()
            );

            notificationService.sendPaymentSuccessToCustomer(
                    orderId,
                    customerEmail != null ? customerEmail : "N/A",
                    amount,
                    currency
            );
        } catch (Exception e) {
            logger.error("Error during payment success processing for orderId: {}, PI ID: {}. Error: {}",
                    orderId, paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Helper method for tests - handles a failed payment
     *
     * @param orderId The order ID
     * @param paymentIntentId The Stripe payment intent ID
     * @param customerEmail The customer's email
     * @param failureReason The reason for the payment failure
     */
    void handlePaymentIntentFailed(Long orderId, String paymentIntentId, String customerEmail,
                                   String failureReason) {
        try {
            // Update order status through the OrderService
            orderService.processPaymentFailure(orderId, paymentIntentId, failureReason);
            logger.info("OrderService successfully processed payment failure for orderId: {}", orderId);

            // Send notification using the NotificationService
            notificationService.sendPaymentFailureToCustomer(
                    orderId,
                    customerEmail != null ? customerEmail : "N/A",
                    failureReason
            );
        } catch (Exception e) {
            logger.error("Error during payment failure processing for orderId: {}, PI ID: {}. Error: {}",
                    orderId, paymentIntentId, e.getMessage(), e);
        }
    }
}