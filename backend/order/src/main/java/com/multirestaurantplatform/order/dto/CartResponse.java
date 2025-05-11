package com.multirestaurantplatform.order.dto;

import java.math.BigDecimal;
import java.util.List;

public class CartResponse {
    private String userId;
    private Long restaurantId;
    private String restaurantName;
    private List<CartItemResponse> items;
    private BigDecimal cartTotalPrice;

    // Constructors, Getters, and Setters
    public CartResponse() {
    }

    public CartResponse(String userId, Long restaurantId, String restaurantName, List<CartItemResponse> items, BigDecimal cartTotalPrice) {
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.items = items;
        this.cartTotalPrice = cartTotalPrice;
    }

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

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }

    public BigDecimal getCartTotalPrice() {
        return cartTotalPrice;
    }

    public void setCartTotalPrice(BigDecimal cartTotalPrice) {
        this.cartTotalPrice = cartTotalPrice;
    }
}