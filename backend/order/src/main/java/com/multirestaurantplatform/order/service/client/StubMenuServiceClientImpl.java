// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/client/StubMenuServiceClientImpl.java
package com.multirestaurantplatform.order.service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap; // Use ConcurrentHashMap for thread safety if needed, though for tests HashMap is often fine.

@Component
public class StubMenuServiceClientImpl implements MenuServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(StubMenuServiceClientImpl.class);
    // Changed to ConcurrentHashMap to allow modification at runtime by tests
    private final Map<Long, MenuItemDetailsDto> mockMenuDatabase = new ConcurrentHashMap<>();

    public StubMenuServiceClientImpl() {
        // Populate with some initial mock data if absolutely necessary for other tests,
        // but for the E2E test, we'll add items dynamically.
        // It's often cleaner to start with an empty mock DB for E2E tests that set up their own data.
        // For example:
        // mockMenuDatabase.put(99901L, new MenuItemDetailsDto(99901L, "Legacy Burger", new BigDecimal("9.99"), 999L, "Legacy Restaurant", true));
        logger.info("StubMenuServiceClientImpl initialized. Current mock DB size: {}", mockMenuDatabase.size());
    }

    @Override
    public Optional<MenuItemDetailsDto> getMenuItemDetails(Long menuItemId, Long restaurantId) {
        logger.debug("StubMenuServiceClient: Fetching menuItemId: {}, restaurantId: {}", menuItemId, restaurantId);
        MenuItemDetailsDto item = mockMenuDatabase.get(menuItemId);
        if (item != null && item.getRestaurantId().equals(restaurantId)) {
            logger.debug("StubMenuServiceClient: Found item: {}", item.getName());
            return Optional.of(new MenuItemDetailsDto( // Return a copy to prevent modification of the stub's internal state
                    item.getId(),
                    item.getName(),
                    item.getPrice(),
                    item.getRestaurantId(),
                    item.getRestaurantName(),
                    item.isAvailable()
            ));
        }
        if (item != null && !item.getRestaurantId().equals(restaurantId)) {
            logger.warn("StubMenuServiceClient: Item {} found but belongs to restaurant {}, expected {}",
                    menuItemId, item.getRestaurantId(), restaurantId);
        } else if (item == null) {
            logger.warn("StubMenuServiceClient: Item {} not found in mock database.", menuItemId);
        }
        return Optional.empty();
    }

    /**
     * Allows tests to dynamically add menu item details to the mock database.
     * @param itemDetails The MenuItemDetailsDto to add.
     */
    public void addMenuItemDetails(MenuItemDetailsDto itemDetails) {
        if (itemDetails != null && itemDetails.getId() != null) {
            mockMenuDatabase.put(itemDetails.getId(), itemDetails);
            logger.info("StubMenuServiceClient: Added/Updated mock menu item: ID={}, Name={}, RestaurantID={}",
                    itemDetails.getId(), itemDetails.getName(), itemDetails.getRestaurantId());
        } else {
            logger.warn("StubMenuServiceClient: Attempted to add null or ID-less MenuItemDetailsDto.");
        }
    }

    /**
     * Clears all items from the mock database. Useful for test cleanup.
     */
    public void clearMockDatabase() {
        mockMenuDatabase.clear();
        logger.info("StubMenuServiceClient: Mock database cleared.");
    }
}
