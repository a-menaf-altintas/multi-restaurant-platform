package com.multirestaurantplatform.payment.controller;

import com.multirestaurantplatform.payment.service.StripeService;
import com.multirestaurantplatform.payment.service.impl.StripeServiceImpl; // For PaymentProcessingException
import com.stripe.exception.SignatureVerificationException;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stripe-webhooks") // Endpoint for Stripe to send events
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeService stripeService;

    // Using constructor injection
    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/events") // Stripe will POST to this specific path
    @Hidden // Hide from Swagger UI as it's not for direct user consumption
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload, // Raw request body
            @RequestHeader("Stripe-Signature") String sigHeader) { // Stripe-Signature header

        logger.info("Received Stripe webhook event. Signature: {}", sigHeader != null ? "Present" : "Missing");
        if (sigHeader == null) {
            logger.warn("Missing Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing Stripe-Signature header");
        }

        try {
            stripeService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok("Event received");
        } catch (SignatureVerificationException e) {
            logger.warn("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature verification failed.");
        } catch (StripeServiceImpl.PaymentProcessingException e) {
            logger.error("Error processing webhook event: {}", e.getMessage());
            // For other processing errors, Stripe generally expects a 5xx,
            // but a 4xx might be okay if it's a payload issue not caught by constructEvent.
            // For Stripe to retry, you might return a 5xx status. For now, 400.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error processing webhook: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error handling webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error.");
        }
    }
}