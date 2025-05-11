package com.multirestaurantplatform.order.model.cart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Cart {
    private String userId; // Or session ID
    private Long restaurantId; // A cart is typically associated with a single restaurant
    private String restaurantName; // For display convenience
    private List<CartItem> items;
    private BigDecimal cartTotalPrice;

    public Cart(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
        this.cartTotalPrice = BigDecimal.ZERO;
    }

    public Cart(String userId, Long restaurantId, String restaurantName) {
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.items = new ArrayList<>();
        this.cartTotalPrice = BigDecimal.ZERO;
    }


    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
        recalculateCartTotalPrice();
    }

    public BigDecimal getCartTotalPrice() {
        return cartTotalPrice;
    }

    // Internal method to recalculate total
    public void recalculateCartTotalPrice() {
        this.cartTotalPrice = items.stream()
                                   .map(CartItem::getTotalPrice)
                                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cart cart = (Cart) o;
        return Objects.equals(userId, cart.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "Cart{" +
                "userId='" + userId + '\'' +
                ", restaurantId=" + restaurantId +
                ", restaurantName='" + restaurantName + '\'' +
                ", itemsCount=" + (items != null ? items.size() : 0) +
                ", cartTotalPrice=" + cartTotalPrice +
                '}';
    }
}