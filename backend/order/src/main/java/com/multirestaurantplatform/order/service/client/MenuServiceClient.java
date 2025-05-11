package com.multirestaurantplatform.order.service.client;

import java.util.Optional;

public interface MenuServiceClient {
    /**
     * Fetches details of a menu item.
     * @param menuItemId The ID of the menu item.
     * @param restaurantId The ID of the restaurant (for context, though menuItemId might be globally unique or unique within restaurant).
     * @return Optional containing MenuItemDetailsDto if found and available, empty otherwise.
     */
    Optional<MenuItemDetailsDto> getMenuItemDetails(Long menuItemId, Long restaurantId);
}