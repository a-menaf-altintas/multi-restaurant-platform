package com.multirestaurantplatform.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// Removed Optional from here as we are not using it anymore
// import java.util.Optional; // No longer needed for fields

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRestaurantRequestDto {

    // Fields are now plain String or Boolean, allowing them to be null if not provided in JSON
    // Validation annotations will apply if the field is provided (not null)
    // If a field is null, it means the client didn't intend to update it.

    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    private String name; // Can be null

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description; // Can be null

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address; // Can be null

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phoneNumber; // Can be null

    @Email(message = "Restaurant email should be valid")
    @Size(max = 100, message = "Restaurant email cannot exceed 100 characters")
    private String email; // Can be null

    private Boolean isActive; // Can be null (use Boolean wrapper type)

}
