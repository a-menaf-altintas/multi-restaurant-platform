package com.multirestaurantplatform.restaurant.service;

public interface RestaurantSecurityService {

    /**
     * Checks if the currently authenticated user (identified by username) is an administrator
     * for the specified restaurant.
     *
     * @param restaurantId The ID of the restaurant to check.
     * @param username The username of the authenticated user.
     * @return true if the user is an administrator for the restaurant, false otherwise.
     */
    boolean isRestaurantAdminForRestaurant(Long restaurantId, String username);
}
