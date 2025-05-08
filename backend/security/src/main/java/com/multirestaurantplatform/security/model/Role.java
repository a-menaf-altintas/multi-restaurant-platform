package com.multirestaurantplatform.security.model;

/**
 * Defines the user roles within the application.
 * Corresponds to authorities in Spring Security.
 */
public enum Role {
    CUSTOMER,        // Regular customer placing orders
    RESTAURANT_ADMIN,// Manages a specific restaurant (menus, orders, settings)
    ADMIN            // Platform administrator (manages restaurants, users, platform settings)
}