package com.multirestaurantplatform.order.service.client;

import java.math.BigDecimal;

public class MenuItemDetailsDto {
    private Long id;
    private String name;
    private BigDecimal price;
    private Long restaurantId;
    private String restaurantName; // Optional, but useful
    private boolean available;

    // Constructors, Getters, Setters
    public MenuItemDetailsDto() {}

    public MenuItemDetailsDto(Long id, String name, BigDecimal price, Long restaurantId, String restaurantName, boolean available) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.available = available;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}