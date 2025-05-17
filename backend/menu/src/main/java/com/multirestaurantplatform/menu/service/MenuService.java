// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/service/MenuService.java
package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.menu.dto.*;

import java.util.List;

public interface MenuService {

    /**
     * Creates a new menu for a given restaurant.
     *
     * @param createMenuRequestDto DTO containing details for the new menu, including restaurantId.
     * @return MenuResponseDto of the created menu.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the specified restaurant does not exist.
     * @throws com.multirestaurantplatform.common.exception.ConflictException if a menu with the same name already exists for the restaurant.
     */
    MenuResponseDto createMenu(CreateMenuRequestDto createMenuRequestDto);

    /**
     * Finds a menu by its unique ID.
     *
     * @param menuId The ID of the menu to find.
     * @return MenuResponseDto of the found menu.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the menu with the given ID is not found.
     */
    MenuResponseDto findMenuById(Long menuId);

    /**
     * Finds all menus associated with a specific restaurant ID.
     *
     * @param restaurantId The ID of the restaurant.
     * @return A list of MenuResponseDto for the specified restaurant.
     * Returns an empty list if the restaurant has no menus or does not exist (though ideally, check restaurant existence separately if needed).
     */
    List<MenuResponseDto> findMenusByRestaurantId(Long restaurantId);

    /**
     * Finds all active menus associated with a specific restaurant ID.
     *
     * @param restaurantId The ID of the restaurant.
     * @return A list of active MenuResponseDto for the specified restaurant.
     * Returns an empty list if the restaurant has no active menus.
     */
    List<MenuResponseDto> findActiveMenusByRestaurantId(Long restaurantId);

    /**
     * Updates an existing menu.
     *
     * @param menuId The ID of the menu to update.
     * @param updateMenuRequestDto DTO containing the fields to update. Null fields in DTO are ignored.
     * @return MenuResponseDto of the updated menu.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the menu with the given ID is not found.
     * @throws com.multirestaurantplatform.common.exception.ConflictException if updating the name causes a conflict with another menu in the same restaurant.
     */
    MenuResponseDto updateMenu(Long menuId, UpdateMenuRequestDto updateMenuRequestDto);

    /**
     * Deletes a menu by its ID.
     * This could be a hard delete or a soft delete (setting isActive to false).
     * The implementation will define the exact behavior.
     *
     * @param menuId The ID of the menu to delete.
     * @throws com.multirestaurantplatform.common.exception.ResourceNotFoundException if the menu with the given ID is not found (for hard delete scenarios).
     */
    void deleteMenu(Long menuId);


    // Methods for MenuItems
    MenuItemResponseDto addMenuItemToMenu(CreateMenuItemRequestDto menuItemDto); // Changed: menuId is in DTO
    MenuItemResponseDto getMenuItemById(Long menuItemId);
    List<MenuItemResponseDto> getMenuItemsByMenuId(Long menuId);
    List<MenuItemResponseDto> getActiveMenuItemsByMenuId(Long menuId);
    MenuItemResponseDto updateMenuItem(Long menuItemId, UpdateMenuItemRequestDto menuItemDto);
    void deleteMenuItem(Long menuItemId); // Soft delete by default (sets isActive=false)
}
