// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/service/MenuServiceImpl.java
package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.menu.dto.CreateMenuItemRequestDto; // Added
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuItemResponseDto; // Added
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuItemRequestDto; // Added
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.model.MenuItem; // Added
import com.multirestaurantplatform.menu.repository.MenuItemRepository; // Added
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final MenuRepository menuRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository; // Injected MenuItemRepository

    // --- Helper Methods for Menu ---

    /**
     * Helper method to find a Menu entity by its ID or throw ResourceNotFoundException.
     * @param menuId The ID of the menu.
     * @return The found Menu entity.
     */
    private Menu findMenuEntityById(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> {
                    LOGGER.warn("Menu not found with ID: {}", menuId);
                    return new ResourceNotFoundException("Menu not found with ID: " + menuId);
                });
    }

    /**
     * Helper method to map a Menu entity to a MenuResponseDto.
     * @param menu The Menu entity.
     * @return The corresponding MenuResponseDto.
     */
    private MenuResponseDto mapToMenuResponseDto(Menu menu) {
        if (menu == null) {
            return null;
        }
        return new MenuResponseDto(
                menu.getId(),
                menu.getName(),
                menu.getDescription(),
                menu.isActive(),
                menu.getRestaurant() != null ? menu.getRestaurant().getId() : null,
                menu.getCreatedAt(),
                menu.getUpdatedAt()
        );
    }

    // --- Helper Methods for MenuItem ---

    /**
     * Helper method to find a MenuItem entity by its ID or throw ResourceNotFoundException.
     * @param menuItemId The ID of the menu item.
     * @return The found MenuItem entity.
     */
    private MenuItem findMenuItemEntityById(Long menuItemId) {
        return menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> {
                    LOGGER.warn("MenuItem not found with ID: {}", menuItemId);
                    return new ResourceNotFoundException("MenuItem not found with ID: " + menuItemId);
                });
    }

    /**
     * Helper method to map a MenuItem entity to a MenuItemResponseDto.
     * @param menuItem The MenuItem entity.
     * @return The corresponding MenuItemResponseDto.
     */
    private MenuItemResponseDto mapToMenuItemResponseDto(MenuItem menuItem) {
        if (menuItem == null) {
            return null;
        }
        return new MenuItemResponseDto(
                menuItem.getId(),
                menuItem.getName(),
                menuItem.getDescription(),
                menuItem.getPrice(),
                menuItem.getImageUrl(),
                menuItem.isActive(),
                menuItem.getMenu() != null ? menuItem.getMenu().getId() : null,
                menuItem.getDietaryInformation(),
                menuItem.getCreatedAt(),
                menuItem.getUpdatedAt()
        );
    }

    // --- Menu Service Implementations (Existing) ---

    @Override
    @Transactional
    public MenuResponseDto createMenu(CreateMenuRequestDto createMenuRequestDto) {
        LOGGER.info("Attempting to create menu with name: {} for restaurant ID: {}", createMenuRequestDto.getName(), createMenuRequestDto.getRestaurantId());
        Restaurant restaurant = restaurantRepository.findById(createMenuRequestDto.getRestaurantId())
                .orElseThrow(() -> {
                    LOGGER.warn("Restaurant not found with ID: {} during menu creation", createMenuRequestDto.getRestaurantId());
                    return new ResourceNotFoundException("Restaurant not found with ID: " + createMenuRequestDto.getRestaurantId() + " while creating menu.");
                });

        menuRepository.findByRestaurantIdAndNameIgnoreCase(createMenuRequestDto.getRestaurantId(), createMenuRequestDto.getName())
                .ifPresent(existingMenu -> {
                    LOGGER.warn("Menu creation failed: name '{}' already exists for restaurant ID {}.", createMenuRequestDto.getName(), createMenuRequestDto.getRestaurantId());
                    throw new ConflictException("Menu with name '" + createMenuRequestDto.getName() + "' already exists for this restaurant.");
                });

        Menu menu = new Menu();
        menu.setName(createMenuRequestDto.getName());
        menu.setDescription(createMenuRequestDto.getDescription());
        menu.setRestaurant(restaurant);
        menu.setActive(true);

        Menu savedMenu = menuRepository.save(menu);
        LOGGER.info("Menu created successfully with ID: {}", savedMenu.getId());
        return mapToMenuResponseDto(savedMenu);
    }

    @Override
    @Transactional(readOnly = true)
    public MenuResponseDto findMenuById(Long menuId) {
        LOGGER.debug("Attempting to find menu with ID: {}", menuId);
        Menu menu = findMenuEntityById(menuId);
        return mapToMenuResponseDto(menu);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuResponseDto> findMenusByRestaurantId(Long restaurantId) {
        LOGGER.debug("Fetching all menus for restaurant ID: {}", restaurantId);
        List<Menu> menus = menuRepository.findByRestaurantId(restaurantId);
        return menus.stream()
                .map(this::mapToMenuResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuResponseDto> findActiveMenusByRestaurantId(Long restaurantId) {
        LOGGER.debug("Fetching active menus for restaurant ID: {}", restaurantId);
        List<Menu> activeMenus = menuRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
        return activeMenus.stream()
                .map(this::mapToMenuResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MenuResponseDto updateMenu(Long menuId, UpdateMenuRequestDto updateMenuRequestDto) {
        LOGGER.info("Attempting to update menu with ID: {}", menuId);
        Menu menu = findMenuEntityById(menuId);

        if (StringUtils.hasText(updateMenuRequestDto.getName()) && !updateMenuRequestDto.getName().equalsIgnoreCase(menu.getName())) {
            menuRepository.findByRestaurantIdAndNameIgnoreCase(menu.getRestaurant().getId(), updateMenuRequestDto.getName())
                    .ifPresent(existingMenu -> {
                        if (!existingMenu.getId().equals(menuId)) {
                            LOGGER.warn("Menu update failed for ID {}: name '{}' already exists for restaurant ID {}.", menuId, updateMenuRequestDto.getName(), menu.getRestaurant().getId());
                            throw new ConflictException("Another menu with name '" + updateMenuRequestDto.getName() + "' already exists for this restaurant.");
                        }
                    });
            menu.setName(updateMenuRequestDto.getName());
        }

        if (updateMenuRequestDto.getDescription() != null) {
            menu.setDescription(updateMenuRequestDto.getDescription());
        }

        if (updateMenuRequestDto.getIsActive() != null) {
            menu.setActive(updateMenuRequestDto.getIsActive());
        }

        Menu updatedMenu = menuRepository.save(menu);
        LOGGER.info("Menu with ID: {} updated successfully.", updatedMenu.getId());
        return mapToMenuResponseDto(updatedMenu);
    }

    @Override
    @Transactional
    public void deleteMenu(Long menuId) {
        LOGGER.info("Attempting to soft delete menu with ID: {}", menuId);
        Menu menu = findMenuEntityById(menuId);
        menu.setActive(false); // Soft delete
        // Also deactivate all its items
        List<MenuItem> items = menuItemRepository.findByMenuId(menuId);
        for (MenuItem item : items) {
            item.setActive(false);
            menuItemRepository.save(item);
        }
        menuRepository.save(menu);
        LOGGER.info("Menu with ID: {} and its items soft deleted (set to inactive) successfully.", menuId);
    }

    // --- MenuItem Service Implementations (New) ---

    @Override
    @Transactional
    public MenuItemResponseDto addMenuItemToMenu(CreateMenuItemRequestDto createMenuItemRequestDto) {
        LOGGER.info("Attempting to add menu item '{}' to menu ID: {}", createMenuItemRequestDto.getName(), createMenuItemRequestDto.getMenuId());
        Menu menu = findMenuEntityById(createMenuItemRequestDto.getMenuId());

        if (!menu.isActive()) {
            LOGGER.warn("Cannot add item to inactive menu ID: {}", menu.getId());
            throw new ConflictException("Cannot add item to an inactive menu. Please activate the menu first.");
        }

        menuItemRepository.findByMenuIdAndNameIgnoreCase(menu.getId(), createMenuItemRequestDto.getName())
                .ifPresent(existingItem -> {
                    LOGGER.warn("MenuItem creation failed: name '{}' already exists in menu ID {}.", createMenuItemRequestDto.getName(), menu.getId());
                    throw new ConflictException("MenuItem with name '" + createMenuItemRequestDto.getName() + "' already exists in this menu.");
                });

        MenuItem menuItem = new MenuItem();
        menuItem.setName(createMenuItemRequestDto.getName());
        menuItem.setDescription(createMenuItemRequestDto.getDescription());
        menuItem.setPrice(createMenuItemRequestDto.getPrice());
        menuItem.setImageUrl(createMenuItemRequestDto.getImageUrl());
        menuItem.setDietaryInformation(createMenuItemRequestDto.getDietaryInformation());
        menuItem.setMenu(menu);
        menuItem.setActive(createMenuItemRequestDto.getIsActive() != null ? createMenuItemRequestDto.getIsActive() : true);

        MenuItem savedMenuItem = menuItemRepository.save(menuItem);
        LOGGER.info("MenuItem created successfully with ID: {} for menu ID: {}", savedMenuItem.getId(), menu.getId());
        return mapToMenuItemResponseDto(savedMenuItem);
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponseDto getMenuItemById(Long menuItemId) {
        LOGGER.debug("Attempting to find menu item with ID: {}", menuItemId);
        MenuItem menuItem = findMenuItemEntityById(menuItemId);
        return mapToMenuItemResponseDto(menuItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponseDto> getMenuItemsByMenuId(Long menuId) {
        LOGGER.debug("Fetching all menu items for menu ID: {}", menuId);
        if (!menuRepository.existsById(menuId)) {
            LOGGER.warn("Menu not found with ID: {} when trying to fetch its items.", menuId);
            throw new ResourceNotFoundException("Menu not found with ID: " + menuId);
        }
        List<MenuItem> menuItems = menuItemRepository.findByMenuId(menuId);
        return menuItems.stream()
                .map(this::mapToMenuItemResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponseDto> getActiveMenuItemsByMenuId(Long menuId) {
        LOGGER.debug("Fetching active menu items for menu ID: {}", menuId);
        Menu menu = findMenuEntityById(menuId); // Ensures menu exists
        if (!menu.isActive()) {
            LOGGER.info("Fetching active items for an inactive menu ID: {}. Returning empty list.", menuId);
            return List.of(); // Or throw an exception if preferred
        }
        List<MenuItem> menuItems = menuItemRepository.findByMenuIdAndIsActiveTrue(menuId);
        return menuItems.stream()
                .map(this::mapToMenuItemResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MenuItemResponseDto updateMenuItem(Long menuItemId, UpdateMenuItemRequestDto updateMenuItemRequestDto) {
        LOGGER.info("Attempting to update menu item with ID: {}", menuItemId);
        MenuItem menuItem = findMenuItemEntityById(menuItemId);

        if (StringUtils.hasText(updateMenuItemRequestDto.getName()) &&
                !updateMenuItemRequestDto.getName().equalsIgnoreCase(menuItem.getName())) {
            menuItemRepository.findByMenuIdAndNameIgnoreCase(menuItem.getMenu().getId(), updateMenuItemRequestDto.getName())
                    .ifPresent(existingItem -> {
                        if (!existingItem.getId().equals(menuItemId)) {
                            LOGGER.warn("MenuItem update failed for ID {}: name '{}' already exists in menu ID {}.",
                                    menuItemId, updateMenuItemRequestDto.getName(), menuItem.getMenu().getId());
                            throw new ConflictException("Another MenuItem with name '" + updateMenuItemRequestDto.getName() +
                                    "' already exists in this menu.");
                        }
                    });
            menuItem.setName(updateMenuItemRequestDto.getName());
        }

        if (updateMenuItemRequestDto.getDescription() != null) {
            menuItem.setDescription(updateMenuItemRequestDto.getDescription());
        }
        if (updateMenuItemRequestDto.getPrice() != null) {
            menuItem.setPrice(updateMenuItemRequestDto.getPrice());
        }
        if (updateMenuItemRequestDto.getImageUrl() != null) {
            menuItem.setImageUrl(updateMenuItemRequestDto.getImageUrl());
        }
        if (updateMenuItemRequestDto.getIsActive() != null) {
            menuItem.setActive(updateMenuItemRequestDto.getIsActive());
        }
        if (updateMenuItemRequestDto.getDietaryInformation() != null) {
            menuItem.setDietaryInformation(updateMenuItemRequestDto.getDietaryInformation());
        }

        MenuItem updatedMenuItem = menuItemRepository.save(menuItem);
        LOGGER.info("MenuItem with ID: {} updated successfully.", updatedMenuItem.getId());
        return mapToMenuItemResponseDto(updatedMenuItem);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long menuItemId) {
        LOGGER.info("Attempting to soft delete menu item with ID: {}", menuItemId);
        MenuItem menuItem = findMenuItemEntityById(menuItemId);
        menuItem.setActive(false); // Soft delete
        menuItemRepository.save(menuItem);
        LOGGER.info("MenuItem with ID: {} soft deleted (set to inactive) successfully.", menuItemId);
    }
}
