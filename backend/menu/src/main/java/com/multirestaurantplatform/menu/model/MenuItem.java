package com.multirestaurantplatform.menu.model;

import com.multirestaurantplatform.common.model.BaseEntity;
import jakarta.persistence.*;
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
@Entity
@Table(name = "menu_items")
public class MenuItem extends BaseEntity {

    @NotBlank(message = "Menu item name cannot be blank")
    @Size(min = 2, max = 100, message = "Menu item name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false, length = 100) // All attributes in one @Column
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(name = "description", length = 1000) // All attributes in one @Column
    private String description;

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Column(name = "price", nullable = false, precision = 10, scale = 2) // All attributes in one @Column
    private BigDecimal price;

    @Column(name = "image_url", length = 2048) // All attributes in one @Column
    private String imageUrl;

    @Column(name = "is_active", nullable = false) // All attributes in one @Column
    private boolean isActive = true;

    @Size(max = 500, message = "Dietary information cannot exceed 500 characters")
    @Column(name = "dietary_information", length = 500, nullable = true) // Added @Column, assuming it's persistent and nullable
    private String dietaryInformation;

    @NotNull(message = "Menu item must be associated with a menu")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false) // This uses @JoinColumn, not @Column, which is correct
    private Menu menu;
}