package com.multirestaurantplatform.payment.service;

public interface StripeService {

    /**
     * Creates a PaymentIntent with Stripe.
     *
     * @param amount The amount for the payment in the smallest currency unit (e.g., cents).
     * @param currency The 3-letter ISO currency code (e.g., "usd", "cad").
     * @param orderId Your internal order ID, to be stored as metadata.
     * @param customerEmail The email of the customer, for Stripe's records and receipts.
     * @return The client secret of the created PaymentIntent.
     * @throws com.multirestaurantplatform.payment.service.impl.StripeServiceImpl.PaymentProcessingException if there's an error.
     */
    String createPaymentIntent(long amount, String currency, String orderId, String customerEmail)
            throws com.multirestaurantplatform.payment.service.impl.StripeServiceImpl.PaymentProcessingException;
}