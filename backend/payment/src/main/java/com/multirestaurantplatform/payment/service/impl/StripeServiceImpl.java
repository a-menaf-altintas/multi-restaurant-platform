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
import com.stripe.model.EventDataObjectDeserializer;
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

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("StripeService initialized with API key. Webhook secret loaded: {}",
                webhookSecret != null && !webhookSecret.startsWith("whsec_YOUR_FALLBACK") ?
                        "[PRESENT]" : "[MISSING or FALLBACK]");
    }

    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
        public PaymentProcessingException(String message) {
            super(message);
        }
    }

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

    @Override
    public void handleWebhookEvent(String payload, String sigHeader)
            throws SignatureVerificationException, PaymentProcessingException {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Webhook error: Invalid Stripe signature. Message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Webhook error: Could not parse payload or other construction error. Message: {}",
                    e.getMessage());
            throw new PaymentProcessingException("Error constructing webhook event: " + e.getMessage(), e);
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            logger.warn("Webhook warning: Deserialization of event data object failed for event ID: {} Type: {}",
                    event.getId(), event.getType());
        }

        logger.info("Received Stripe Event: Id='{}', Type='{}', Livemode='{}'",
                event.getId(), event.getType(), event.getLivemode());

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

    private void handlePaymentIntentSucceeded(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof PaymentIntent)) {
            logger.warn("payment_intent.succeeded event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.",
                    event.getId());
            return;
        }

        PaymentIntent paymentIntentSucceeded = (PaymentIntent) stripeObject;
        String orderIdStr = paymentIntentSucceeded.getMetadata().get("order_id");
        String piId = paymentIntentSucceeded.getId();

        // Attempt to get customer email from metadata first, then from receipt_email
        String customerEmail = paymentIntentSucceeded.getMetadata().get("customer_email_for_order");
        if (customerEmail == null || customerEmail.isBlank()) {
            customerEmail = paymentIntentSucceeded.getReceiptEmail();
        }

        logger.info("PaymentIntent Succeeded: PI_ID={}, Amount={}, OrderID (metadata)={}",
                piId, paymentIntentSucceeded.getAmount(), orderIdStr);

        if (orderIdStr == null) {
            logger.warn("PaymentIntent Succeeded (PI ID: {}) but order_id was missing from metadata.", piId);
            return;
        }

        try {
            Long orderId = Long.parseLong(orderIdStr);
            Order order = orderService.processPaymentSuccess(orderId, piId);
            logger.info("OrderService successfully processed payment success for orderId: {}", orderId);

            // Convert Stripe's amount (in cents) to a BigDecimal for notification
            BigDecimal amount = BigDecimal.valueOf(paymentIntentSucceeded.getAmount())
                    .divide(BigDecimal.valueOf(100));

            // Send notifications
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
                    paymentIntentSucceeded.getCurrency()
            );

        } catch (NumberFormatException e) {
            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}",
                    orderIdStr, piId, e);
        } catch (Exception e) {
            logger.error("Error during post-payment success processing for orderId: {}, PI ID: {}. Error: {}",
                    orderIdStr, piId, e.getMessage(), e);
        }
    }

    private void handlePaymentIntentFailed(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof PaymentIntent)) {
            logger.warn("payment_intent.payment_failed event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.",
                    event.getId());
            return;
        }

        PaymentIntent paymentIntentFailed = (PaymentIntent) stripeObject;
        String orderIdStr = paymentIntentFailed.getMetadata().get("order_id");
        String piId = paymentIntentFailed.getId();
        String failureReason = paymentIntentFailed.getLastPaymentError() != null ?
                paymentIntentFailed.getLastPaymentError().getMessage() : "N/A";

        String customerEmail = paymentIntentFailed.getMetadata().get("customer_email_for_order");
        if (customerEmail == null || customerEmail.isBlank()) {
            customerEmail = paymentIntentFailed.getReceiptEmail();
        }

        logger.info("PaymentIntent Failed: PI_ID={}, OrderID (metadata)={}, FailureReason={}",
                piId, orderIdStr, failureReason);

        if (orderIdStr == null) {
            logger.warn("PaymentIntent Failed (PI ID: {}) but order_id was missing from metadata.", piId);
            return;
        }

        try {
            Long orderId = Long.parseLong(orderIdStr);
            orderService.processPaymentFailure(orderId, piId, failureReason);
            logger.info("OrderService successfully processed payment failure for orderId: {}", orderId);

            // Send notification
            notificationService.sendPaymentFailureToCustomer(
                    orderId,
                    customerEmail != null ? customerEmail : "N/A",
                    failureReason
            );

        } catch (NumberFormatException e) {
            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}",
                    orderIdStr, piId, e);
        } catch (Exception e) {
            logger.error("Error during post-payment failure processing for orderId: {}, PI ID: {}. Error: {}",
                    orderIdStr, piId, e.getMessage(), e);
        }
    }
}