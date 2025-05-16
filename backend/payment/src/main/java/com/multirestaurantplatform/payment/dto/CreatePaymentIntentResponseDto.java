package com.multirestaurantplatform.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentResponseDto {
    private String clientSecret;
    private String paymentIntentId; // Optional: also return the PaymentIntent ID for reference
}