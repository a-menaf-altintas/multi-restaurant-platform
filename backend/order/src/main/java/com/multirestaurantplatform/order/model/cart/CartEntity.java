package com.multirestaurantplatform.order.model.cart;

import com.multirestaurantplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a user's shopping cart stored in the database.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
public class CartEntity extends BaseEntity {

    @NotBlank(message = "User ID cannot be blank")
    @Column(nullable = false, unique = true)
    private String userId;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "restaurant_name")
    private String restaurantName;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItemEntity> items = new ArrayList<>();

    @NotNull(message = "Cart total price cannot be null")
    @Column(name = "cart_total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal cartTotalPrice = BigDecimal.ZERO;

    /**
     * Add a cart item to this cart, handling the bidirectional relationship.
     * @param item The cart item to add
     */
    public void addItem(CartItemEntity item) {
        items.add(item);
        item.setCart(this);
        recalculateCartTotalPrice();
    }

    /**
     * Remove a cart item from this cart, handling the bidirectional relationship.
     * @param item The cart item to remove
     */
    public void removeItem(CartItemEntity item) {
        items.remove(item);
        item.setCart(null);
        recalculateCartTotalPrice();
    }

    /**
     * Recalculate the total price of the cart based on all items.
     */
    public void recalculateCartTotalPrice() {
        this.cartTotalPrice = items.stream()
                .map(CartItemEntity::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Updates the restaurant information for this cart.
     * @param restaurantId The ID of the restaurant
     * @param restaurantName The name of the restaurant
     */
    public void updateRestaurantInfo(Long restaurantId, String restaurantName) {
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
    }
}