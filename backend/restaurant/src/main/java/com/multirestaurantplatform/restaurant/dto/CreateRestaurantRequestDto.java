package com.multirestaurantplatform.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRestaurantRequestDto {

    @NotBlank(message = "Restaurant name cannot be blank")
    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotBlank(message = "Address cannot be blank")
    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @NotBlank(message = "Phone number cannot be blank")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phoneNumber;

    @Email(message = "Restaurant email should be valid")
    @Size(max = 100, message = "Restaurant email cannot exceed 100 characters")
    private String email; // Contact email for the restaurant

    // We can add fields for initial restaurant admins (e.g., Set<Long> adminUserIds) later if needed.
    // For now, keep it simple. Admin assignment can be a separate operation or part of an update.
}