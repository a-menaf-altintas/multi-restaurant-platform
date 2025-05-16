package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.order.service.OrderService; // Import OrderService
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
import lombok.RequiredArgsConstructor; // Added for constructor injection
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor // Added for constructor injection of OrderService
public class StripeServiceImpl implements StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeServiceImpl.class);

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final OrderService orderService; // Inject OrderService

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("StripeService initialized with API key. Webhook secret loaded: {}", webhookSecret != null && !webhookSecret.startsWith("whsec_YOUR_FALLBACK") ? "[PRESENT]" : "[MISSING or FALLBACK]");
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
        // ... (existing createPaymentIntent method - no changes needed here) ...
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
                    .putMetadata("order_id", orderId) // Ensure orderId is a String here
                    .putMetadata("customer_email_for_order", customerEmail)
                    .setReceiptEmail(customerEmail);

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            logger.info("PaymentIntent created successfully. Stripe PaymentIntent ID: {}, Order ID: {}, Client Secret: {}",
                    paymentIntent.getId(), orderId, paymentIntent.getClientSecret() != null ? "[PRESENT]" : "[MISSING]");
            if (paymentIntent.getClientSecret() == null) {
                logger.error("Critical: PaymentIntent {} for order {} was created but client_secret is null.", paymentIntent.getId(), orderId);
                throw new PaymentProcessingException("Failed to create PaymentIntent for order " + orderId + ": Client secret was null after creation.");
            }
            return paymentIntent.getClientSecret();

        } catch (StripeException e) {
            logger.error("Stripe API error creating PaymentIntent for orderId {}: {} (Status: {}, Code: {})",
                    orderId, e.getMessage(), e.getStatusCode(), e.getCode(), e);
            throw new PaymentProcessingException("Stripe API error while creating PaymentIntent for order " + orderId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating PaymentIntent for orderId {}: {}", orderId, e.getMessage(), e);
            throw new PaymentProcessingException("Unexpected error while creating PaymentIntent for order " + orderId + ": " + e.getMessage(), e);
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
            logger.error("Webhook error: Could not parse payload or other construction error. Message: {}", e.getMessage());
            throw new PaymentProcessingException("Error constructing webhook event: " + e.getMessage(), e);
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            logger.warn("Webhook warning: Deserialization of event data object failed for event ID: {} Type: {}", event.getId(), event.getType());
        }

        logger.info("Received Stripe Event: Id='{}', Type='{}', Livemode='{}'", event.getId(), event.getType(), event.getLivemode());

        switch (event.getType()) {
            case "payment_intent.succeeded":
                if (stripeObject instanceof PaymentIntent) {
                    PaymentIntent paymentIntentSucceeded = (PaymentIntent) stripeObject;
                    String orderId = paymentIntentSucceeded.getMetadata().get("order_id");
                    String piId = paymentIntentSucceeded.getId();
                    logger.info("PaymentIntent Succeeded: ID={}, Amount={}, OrderID (from metadata)={}",
                            piId, paymentIntentSucceeded.getAmount(), orderId);

                    if (orderId != null) {
                        try {
                            orderService.processPaymentSuccess(Long.parseLong(orderId), piId);
                            logger.info("OrderService successfully processed payment success for orderId: {}", orderId);
                        } catch (NumberFormatException e) {
                            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}", orderId, piId, e);
                        } catch (Exception e) { // Catch exceptions from OrderService
                            logger.error("Error calling OrderService.processPaymentSuccess for orderId: {}, PI ID: {}. Error: {}", orderId, piId, e.getMessage(), e);
                            // Decide if this should re-throw or if Stripe should still get a 200 OK because webhook was valid.
                            // For now, we log and let Stripe get 200 OK for a valid event.
                        }
                    } else {
                        logger.warn("PaymentIntent Succeeded (PI ID: {}) but order_id was missing from metadata.", piId);
                    }
                } else {
                    logger.warn("payment_intent.succeeded event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.", event.getId());
                }
                break;

            case "payment_intent.payment_failed":
                if (stripeObject instanceof PaymentIntent) {
                    PaymentIntent paymentIntentFailed = (PaymentIntent) stripeObject;
                    String orderId = paymentIntentFailed.getMetadata().get("order_id");
                    String piId = paymentIntentFailed.getId();
                    String failureReason = paymentIntentFailed.getLastPaymentError() != null ? paymentIntentFailed.getLastPaymentError().getMessage() : "N/A";
                    logger.info("PaymentIntent Failed: ID={}, OrderID (from metadata)={}, FailureReason={}",
                            piId, orderId, failureReason);

                    if (orderId != null) {
                        try {
                            orderService.processPaymentFailure(Long.parseLong(orderId), piId, failureReason);
                            logger.info("OrderService successfully processed payment failure for orderId: {}", orderId);
                        } catch (NumberFormatException e) {
                            logger.error("Error parsing orderId '{}' from PaymentIntent metadata for PI ID: {}", orderId, piId, e);
                        } catch (Exception e) { // Catch exceptions from OrderService
                            logger.error("Error calling OrderService.processPaymentFailure for orderId: {}, PI ID: {}. Error: {}", orderId, piId, e.getMessage(), e);
                        }
                    } else {
                        logger.warn("PaymentIntent Failed (PI ID: {}) but order_id was missing from metadata.", piId);
                    }
                } else {
                    logger.warn("payment_intent.payment_failed event for event ID: {} but StripeObject was not a PaymentIntent or was null after deserialization.", event.getId());
                }
                break;

            default:
                logger.warn("Unhandled Stripe event type: {}", event.getType());
        }
    }
}