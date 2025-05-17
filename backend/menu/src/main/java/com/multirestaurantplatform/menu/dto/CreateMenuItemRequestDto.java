package com.multirestaurantplatform.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateMenuItemRequestDto {
    @NotBlank
    @Size(min = 2, max = 100)
    private String name;
    @Size(max = 1000)
    private String description;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;
    @Size(max = 2048)
    private String imageUrl;
    @NotNull
    private Long menuId; // To associate with a parent Menu
     @Size(max = 500)
    private String dietaryInformation;
    private Boolean isActive; // Optional, defaults to true in entity
}