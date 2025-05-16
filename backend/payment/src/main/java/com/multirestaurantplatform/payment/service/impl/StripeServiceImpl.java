package com.multirestaurantplatform.payment.service.impl;

import com.multirestaurantplatform.payment.service.StripeService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeServiceImpl implements StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeServiceImpl.class);

    @Value("${stripe.secret.key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("StripeService initialized with API key.");
    }

    /**
     * Custom exception for payment processing errors.
     * Defined as an inner class to minimize new files for now.
     * Can be refactored into its own file in backend/payment/src/main/java/com/multirestaurantplatform/payment/exception/ later.
     */
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

            // Optional: Create or retrieve a Stripe Customer object
            // This allows Stripe to link payments to a customer, send receipts, and save payment methods
            // For simplicity, we are not creating a Stripe Customer here yet, but adding email to PaymentIntent
            // com.stripe.model.Customer stripeCustomer = findOrCreateStripeCustomer(customerEmail, "Customer for order " + orderId);

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.toLowerCase())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build())
                .putMetadata("order_id", orderId)
                .putMetadata("customer_email_for_order", customerEmail); // Storing email in metadata for reference

            // If you want Stripe to handle sending email receipts, you can set the 'receipt_email' parameter
            // Note: This is generally done if you have a Stripe Customer object associated, or directly on PI.
             paramsBuilder.setReceiptEmail(customerEmail);
            // if (stripeCustomer != null) {
            //     paramsBuilder.setCustomer(stripeCustomer.getId());
            // }


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

    // Example stub for finding/creating a Stripe customer - can be implemented later
    // private com.stripe.model.Customer findOrCreateStripeCustomer(String email, String name) throws StripeException {
    //     // Logic to search for customer by email, if not found, create new.
    //     // This is a simplified stub.
    //     com.stripe.param.CustomerListParams listParams = com.stripe.param.CustomerListParams.builder().setEmail(email).setLimit(1L).build();
    //     var customers = com.stripe.model.Customer.list(listParams);
    //     if (!customers.getData().isEmpty()) {
    //         return customers.getData().get(0);
    //     } else {
    //         com.stripe.param.CustomerCreateParams customerParams = com.stripe.param.CustomerCreateParams.builder()
    //             .setEmail(email)
    //             .setName(name)
    //             .putMetadata("app_order_id", "Initial Order Link") // Example metadata
    //             .build();
    //         return com.stripe.model.Customer.create(customerParams);
    //     }
    // }
}