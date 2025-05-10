package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant; // Ensure this is the correct model import

import java.util.List;

public interface RestaurantService {

    /**
     * Creates a new restaurant.
     * @param createDto DTO containing data for the new restaurant.
     * @return The created Restaurant entity.
     * @throws com.multirestaurantplatform.common.exception.ConflictException if name or email already exists.
     */
    Restaurant createRestaurant(CreateRestaurantRequestDto createDto);

    /**
     * Finds a restaurant by its ID.
     * @param id The ID of the restaurant.
     * @return The found Restaurant entity.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if restaurant not found.
     */
    Restaurant findRestaurantById(Long id);

    /**
     * Retrieves all restaurants.
     * (Consider adding pagination and filtering in a future iteration)
     * @return A list of all Restaurant entities.
     */
    List<Restaurant> findAllRestaurants();

    /**
     * Updates an existing restaurant.
     * @param id The ID of the restaurant to update.
     * @param updateDto DTO containing data to update.
     * @return The updated Restaurant entity.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if restaurant not found.
     * @throws com.multirestaurantplatform.common.exception.ConflictException if updated name or email conflicts with another restaurant.
     */
    Restaurant updateRestaurant(Long id, UpdateRestaurantRequestDto updateDto);

    /**
     * Deletes a restaurant by its ID.
     * (Consider soft delete by setting isActive=false vs hard delete)
     * @param id The ID of the restaurant to delete.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if restaurant not found.
     */
    void deleteRestaurant(Long id); // For now, a hard delete. Soft delete can be implemented by updating 'isActive'.

    // Future methods could include:
    // Restaurant addAdminToRestaurant(Long restaurantId, Long userId);
    // Restaurant removeAdminFromRestaurant(Long restaurantId, Long userId);
}