package com.multirestaurantplatform.order.model.cart;

import com.multirestaurantplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entity representing an item in a user's shopping cart stored in the database.
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
public class CartItemEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @NotNull(message = "Menu item ID cannot be null")
    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @NotBlank(message = "Menu item name cannot be blank")
    @Column(name = "menu_item_name", nullable = false)
    private String menuItemName;

    @NotNull(message = "Quantity cannot be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

    @NotNull(message = "Unit price cannot be null")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @NotNull(message = "Total price cannot be null")
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Recalculate the total price of this item based on quantity and unit price.
     */
    public void recalculateTotalPrice() {
        if (this.quantity != null && this.unitPrice != null) {
            this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        }
    }

    /**
     * Constructor with all required fields for a cart item.
     */
    public CartItemEntity(Long menuItemId, String menuItemName, Integer quantity, BigDecimal unitPrice) {
        this.menuItemId = menuItemId;
        this.menuItemName = menuItemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        recalculateTotalPrice();
    }

    /**
     * Override setQuantity to ensure total price is recalculated.
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        recalculateTotalPrice();
    }

    /**
     * Override setUnitPrice to ensure total price is recalculated.
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        recalculateTotalPrice();
    }
}