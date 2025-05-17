package com.multirestaurantplatform.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuItemRequestDto {
    @Size(min = 2, max = 100)
    private String name; // All fields are optional for update
    @Size(max = 1000)
    private String description;
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;
    @Size(max = 2048)
    private String imageUrl;
    private Boolean isActive;
    @Size(max = 500)
    private String dietaryInformation;
    // menuId is generally not updatable; items are moved by deleting and re-adding.
}