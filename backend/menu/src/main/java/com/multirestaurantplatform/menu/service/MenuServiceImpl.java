// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/service/MenuServiceImpl.java
package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant; // Assuming this is the correct model
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository; // Assuming this is the correct repository

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
    private final RestaurantRepository restaurantRepository; // To validate restaurantId

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
                menu.getRestaurant() != null ? menu.getRestaurant().getId() : null, // Handle potential null restaurant
                menu.getCreatedAt(),
                menu.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public MenuResponseDto createMenu(CreateMenuRequestDto createMenuRequestDto) {
        LOGGER.info("Attempting to create menu with name: {} for restaurant ID: {}", createMenuRequestDto.getName(), createMenuRequestDto.getRestaurantId());

        // Validate that the restaurantId from the DTO corresponds to an existing Restaurant
        Restaurant restaurant = restaurantRepository.findById(createMenuRequestDto.getRestaurantId())
                .orElseThrow(() -> {
                    LOGGER.warn("Restaurant not found with ID: {} during menu creation", createMenuRequestDto.getRestaurantId());
                    return new ResourceNotFoundException("Restaurant not found with ID: " + createMenuRequestDto.getRestaurantId() + " while creating menu.");
                });

        // Check if a menu with the same name already exists for the given restaurant
        menuRepository.findByRestaurantIdAndNameIgnoreCase(createMenuRequestDto.getRestaurantId(), createMenuRequestDto.getName())
                .ifPresent(existingMenu -> {
                    LOGGER.warn("Menu creation failed: name '{}' already exists for restaurant ID {}.", createMenuRequestDto.getName(), createMenuRequestDto.getRestaurantId());
                    throw new ConflictException("Menu with name '" + createMenuRequestDto.getName() + "' already exists for this restaurant.");
                });

        Menu menu = new Menu();
        menu.setName(createMenuRequestDto.getName());
        menu.setDescription(createMenuRequestDto.getDescription());
        menu.setRestaurant(restaurant);
        menu.setActive(true); // Default to active

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
        // Optional: Check if restaurant exists first if strict validation is needed before querying menus
        // if (!restaurantRepository.existsById(restaurantId)) {
        //     LOGGER.warn("Attempted to fetch menus for non-existent restaurant ID: {}", restaurantId);
        //     return Collections.emptyList(); // Or throw ResourceNotFoundException
        // }
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

        // If name is being updated, check for name conflicts within the same restaurant
        if (StringUtils.hasText(updateMenuRequestDto.getName()) && !updateMenuRequestDto.getName().equalsIgnoreCase(menu.getName())) {
            menuRepository.findByRestaurantIdAndNameIgnoreCase(menu.getRestaurant().getId(), updateMenuRequestDto.getName())
                    .ifPresent(existingMenu -> {
                        if (!existingMenu.getId().equals(menuId)) { // Ensure it's not the same menu
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

        if (!menu.isActive()) {
            LOGGER.info("Menu with ID: {} is already inactive.", menuId);
            // Optionally, you could throw an exception or just do nothing.
            // For now, we'll just log and proceed (idempotency for deactivation).
        }

        menu.setActive(false);
        menuRepository.save(menu);
        LOGGER.info("Menu with ID: {} soft deleted (set to inactive) successfully.", menuId);
        // For hard delete, it would be:
        // if (!menuRepository.existsById(menuId)) {
        //     throw new ResourceNotFoundException("Menu not found with ID: " + menuId + " for deletion.");
        // }
        // menuRepository.deleteById(menuId);
        // LOGGER.info("Menu with ID: {} hard deleted successfully.", menuId);
    }
}
