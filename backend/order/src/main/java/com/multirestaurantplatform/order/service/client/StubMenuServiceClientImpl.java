package com.multirestaurantplatform.order.service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component; // Or @Service
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// This is a STUB implementation for development and testing of CartService.
// In a real application, this would be a Feign client or similar,
// or a direct call to another module's service.
@Component // Make it a Spring bean so CartService can autowire it
public class StubMenuServiceClientImpl implements MenuServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(StubMenuServiceClientImpl.class);
    private final Map<Long, MenuItemDetailsDto> mockMenuDatabase = new HashMap<>();

    public StubMenuServiceClientImpl() {
        // Populate with some mock data
        // Restaurant 1
        mockMenuDatabase.put(101L, new MenuItemDetailsDto(101L, "Classic Burger", new BigDecimal("12.99"), 1L, "Burger Queen", true));
        mockMenuDatabase.put(102L, new MenuItemDetailsDto(102L, "Cheese Fries", new BigDecimal("5.50"), 1L, "Burger Queen", true));
        mockMenuDatabase.put(103L, new MenuItemDetailsDto(103L, "Milkshake", new BigDecimal("4.00"), 1L, "Burger Queen", false)); // Unavailable

        // Restaurant 2
        mockMenuDatabase.put(201L, new MenuItemDetailsDto(201L, "Margherita Pizza", new BigDecimal("15.00"), 2L, "Pizza Palace", true));
        mockMenuDatabase.put(202L, new MenuItemDetailsDto(202L, "Pepperoni Pizza", new BigDecimal("17.50"), 2L, "Pizza Palace", true));
        logger.info("StubMenuServiceClientImpl initialized with mock data.");
    }

    @Override
    public Optional<MenuItemDetailsDto> getMenuItemDetails(Long menuItemId, Long restaurantId) {
        logger.debug("StubMenuServiceClient: Fetching menuItemId: {}, restaurantId: {}", menuItemId, restaurantId);
        MenuItemDetailsDto item = mockMenuDatabase.get(menuItemId);
        if (item != null && item.getRestaurantId().equals(restaurantId)) {
            return Optional.of(item);
        }
        // If item exists but for wrong restaurant, it's effectively not found for this call context
        if (item != null && !item.getRestaurantId().equals(restaurantId)) {
             logger.warn("StubMenuServiceClient: Item {} found but belongs to restaurant {}, expected {}",
                menuItemId, item.getRestaurantId(), restaurantId);
        }
        return Optional.empty();
    }
}