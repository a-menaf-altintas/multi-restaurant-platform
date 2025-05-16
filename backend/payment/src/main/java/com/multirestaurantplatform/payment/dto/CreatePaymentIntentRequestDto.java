package com.multirestaurantplatform.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentRequestDto {

    @NotNull(message = "Amount cannot be null")
    @Min(value = 50, message = "Amount must be at least 50 (e.g., 0.50 USD/CAD in cents)") // Smallest chargeable amount for Stripe is typically $0.50
    private Long amount; // Amount in smallest currency unit (e.g., cents)

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency; // e.g., "usd", "cad"

    @NotBlank(message = "Order ID cannot be blank")
    private String orderId;

    @NotBlank(message = "Customer email cannot be blank")
    @Email(message = "Customer email should be a valid email address")
    private String customerEmail;
}