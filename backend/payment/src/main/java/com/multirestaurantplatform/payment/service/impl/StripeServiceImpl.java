package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.payment.service.StripeService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException; // Stripe's exception
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject; // For deserialized object
import com.stripe.net.Webhook; // For signature verification
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeServiceImpl implements StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeServiceImpl.class);

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}") // Inject the webhook secret
    private String webhookSecret;

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
        // ... (existing createPaymentIntent method from previous step - no changes here) ...
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
            // Verify the signature and construct the event object
            // This will throw SignatureVerificationException if verification fails
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Webhook error: Invalid Stripe signature. Message: {}", e.getMessage());
            throw e; // Re-throw for the controller to handle as a specific error (e.g., HTTP 400)
        } catch (Exception e) { // Catches JsonSyntaxException etc.
            logger.error("Webhook error: Could not parse payload or other construction error. Message: {}", e.getMessage());
            throw new PaymentProcessingException("Error constructing webhook event: " + e.getMessage(), e);
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            // Deserialization failed, probably due to API version mismatch or an issue with the data
            logger.warn("Webhook warning: Deserialization of event data object failed for event ID: {} Type: {}", event.getId(), event.getType());
            // Depending on the event type, you might still be able to proceed or might need to log and ignore
        }

        logger.info("Received Stripe Event: Id='{}', Type='{}', Livemode='{}'", event.getId(), event.getType(), event.getLivemode());

        // Handle the event
        switch (event.getType()) {
            case "payment_intent.succeeded":
                PaymentIntent paymentIntentSucceeded = (PaymentIntent) stripeObject;
                if (paymentIntentSucceeded != null) {
                    logger.info("PaymentIntent Succeeded: ID={}, Amount={}, OrderID (from metadata)={}",
                            paymentIntentSucceeded.getId(),
                            paymentIntentSucceeded.getAmount(),
                            paymentIntentSucceeded.getMetadata().get("order_id"));
                    // TODO:
                    // 1. Retrieve your internal order using paymentIntentSucceeded.getMetadata().get("order_id")
                    // 2. Mark the order as paid/processing.
                    // 3. Fulfill the order (e.g., notify restaurant, etc.).
                    // 4. Ensure this is idempotent (e.g., check if order already marked as paid).
                } else {
                    logger.warn("payment_intent.succeeded event for event ID: {} but StripeObject was null after deserialization.", event.getId());
                }
                break;
            case "payment_intent.payment_failed":
                PaymentIntent paymentIntentFailed = (PaymentIntent) stripeObject;
                if (paymentIntentFailed != null) {
                    logger.info("PaymentIntent Failed: ID={}, OrderID (from metadata)={}, FailureReason={}",
                            paymentIntentFailed.getId(),
                            paymentIntentFailed.getMetadata().get("order_id"),
                            paymentIntentFailed.getLastPaymentError() != null ? paymentIntentFailed.getLastPaymentError().getMessage() : "N/A");
                    // TODO:
                    // 1. Retrieve your internal order.
                    // 2. Mark the order as payment failed.
                    // 3. Notify the customer.
                } else {
                    logger.warn("payment_intent.payment_failed event for event ID: {} but StripeObject was null after deserialization.", event.getId());
                }
                break;
            // ... handle other event types as needed ...
            // e.g., charge.succeeded, checkout.session.completed (if using Stripe Checkout)
            default:
                logger.warn("Unhandled Stripe event type: {}", event.getType());
        }
    }
}