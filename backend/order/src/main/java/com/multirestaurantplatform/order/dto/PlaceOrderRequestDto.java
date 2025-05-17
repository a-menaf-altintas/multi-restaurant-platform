// File: backend/order/src/main/java/com/multirestaurantplatform/order/dto/PlaceOrderRequestDto.java
package com.multirestaurantplatform.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequestDto {

    // Delivery Address Details
    // These fields are optional if it's a pickup order, but required for delivery.
    // Validation for "required for delivery" would typically be handled in the service layer
    // based on an order type (e.g., PICKUP vs DELIVERY), which we might add later.
    // For now, we'll make them optional at DTO level and let the E2E test provide them for delivery.

    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    private String deliveryAddressLine1;

    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    private String deliveryAddressLine2; // Optional

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String deliveryCity;

    @Size(max = 100, message = "State/Province cannot exceed 100 characters")
    private String deliveryState; // Or province

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String deliveryPostalCode;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String deliveryCountry;

    @Size(max = 20, message = "Contact number cannot exceed 20 characters")
    private String customerContactNumber; // For delivery purposes

    @Size(max = 500, message = "Special instructions cannot exceed 500 characters")
    private String specialInstructions; // Optional

    // We might add an "orderType" (e.g., PICKUP, DELIVERY) field here later
    // to make validation and processing more explicit.
}
