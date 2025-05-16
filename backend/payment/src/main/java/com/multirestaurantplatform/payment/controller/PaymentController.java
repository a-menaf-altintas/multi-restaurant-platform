package com.multirestaurantplatform.payment.controller;

import com.multirestaurantplatform.payment.dto.CreatePaymentIntentRequestDto;
import com.multirestaurantplatform.payment.dto.CreatePaymentIntentResponseDto;
import com.multirestaurantplatform.payment.service.StripeService;
import com.multirestaurantplatform.payment.service.impl.StripeServiceImpl; // For the exception
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "APIs for processing payments with Stripe")
@SecurityRequirement(name = "bearerAuth") // Indicates JWT is generally required
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final StripeService stripeService;

    @PostMapping("/create-intent")
    @PreAuthorize("isAuthenticated()") // Ensures the user is authenticated
    @Operation(summary = "Create a Stripe Payment Intent",
            description = "Initializes a payment with Stripe and returns a client secret to be used by the frontend.",
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
            // Extracting Payment Intent ID from clientSecret is not straightforward.
            // If needed, StripeService's createPaymentIntent could return a wrapper object.
            // For now, let's assume clientSecret is enough for the client.
            // We can modify StripeService to return both if PaymentIntent ID is needed in response.
            // Let's assume the clientSecret contains enough information or pattern to extract PI ID if client needs it.
            // Or, better, we can modify the StripeService to return the PI ID along with client secret.
            // For now, let's just return the clientSecret.
            //
            // To get PI ID correctly:
            // The client_secret format is typically pi_XXXXX_secret_YYYYY.
            // The PI ID is the "pi_XXXXX" part.
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
            // Consider creating a more specific error response DTO
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating PaymentIntent for order ID {}: {}", requestDto.getOrderId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }
}