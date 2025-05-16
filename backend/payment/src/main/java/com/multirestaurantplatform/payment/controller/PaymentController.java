package com.multirestaurantplatform.payment.controller;

import com.multirestaurantplatform.payment.dto.CreatePaymentIntentRequestDto;
import com.multirestaurantplatform.payment.dto.CreatePaymentIntentResponseDto;
import com.multirestaurantplatform.payment.dto.StripePublishableKeyResponseDto; // New DTO import
import com.multirestaurantplatform.payment.service.StripeService;
import com.multirestaurantplatform.payment.service.impl.StripeServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // For injecting property values
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*; // Added GetMapping

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "APIs for processing payments with Stripe and retrieving configuration")
// Removed class-level @SecurityRequirement as get-publishable-key will be public
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final StripeService stripeService;

    @Value("${stripe.publishable.key}") // Inject the publishable key from application.properties
    private String stripePublishableKey;

    @GetMapping("/stripe-publishable-key")
    @Operation(summary = "Get Stripe Publishable Key",
            description = "Retrieves the Stripe publishable key required for frontend Stripe.js initialization. This endpoint is public.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved Stripe publishable key")
            })
    // No @PreAuthorize, this endpoint should be public
    public ResponseEntity<StripePublishableKeyResponseDto> getStripePublishableKey() {
        logger.debug("Request received for Stripe Publishable Key.");
        return ResponseEntity.ok(new StripePublishableKeyResponseDto(stripePublishableKey));
    }

    @PostMapping("/create-intent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a Stripe Payment Intent",
            description = "Initializes a payment with Stripe and returns a client secret to be used by the frontend.",
            security = @SecurityRequirement(name = "bearerAuth"), // Apply security to this specific endpoint
            responses = {
                    @ApiResponse(responseCode = "201", description = "PaymentIntent created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request payload or Stripe error"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid"),
                    @ApiResponse(responseCode = "500", description = "Internal server error or unexpected Stripe error")
            })
    public ResponseEntity<?> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequestDto requestDto) {
        try {
            logger.info("Received request to create PaymentIntent for order ID: {}", requestDto.getOrderId());
            String clientSecret = stripeService.createPaymentIntent(
                    requestDto.getAmount(),
                    requestDto.getCurrency(),
                    requestDto.getOrderId(),
                    requestDto.getCustomerEmail()
            );

            String paymentIntentId = null;
            if (clientSecret != null && clientSecret.contains("_secret_")) {
                paymentIntentId = clientSecret.substring(0, clientSecret.indexOf("_secret_"));
            }

            logger.info("PaymentIntent created for order ID: {}. Payment Intent ID: {}", requestDto.getOrderId(), paymentIntentId);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new CreatePaymentIntentResponseDto(clientSecret, paymentIntentId));

        } catch (StripeServiceImpl.PaymentProcessingException e) {
            logger.error("PaymentProcessingException for order ID {}: {}", requestDto.getOrderId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating PaymentIntent for order ID {}: {}", requestDto.getOrderId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }
}