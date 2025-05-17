// File: backend/api/src/main/java/com/multirestaurantplatform/api/controller/MenuController.java
package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.menu.dto.CreateMenuItemRequestDto;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuItemResponseDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuItemRequestDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.service.MenuService;
// Import MenuSecurityService if you need to inject its bean, though for SpEL it's usually not directly injected here
// import com.multirestaurantplatform.menu.service.MenuSecurityService;
// Import RestaurantSecurityService if you need to inject its bean for SpEL usage
// import com.multirestaurantplatform.restaurant.service.RestaurantSecurityService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1") // Base path adjusted for new item endpoints
@RequiredArgsConstructor
@Tag(name = "Menu Management", description = "APIs for managing restaurant menus and menu items")
@SecurityRequirement(name = "bearerAuth") // Indicates JWT is generally required
public class MenuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);
    private final MenuService menuService;
    // No need to inject security services here if they are only used in @PreAuthorize SpEL expressions
    // and are correctly named Spring beans (e.g., @Service("menuSecurityServiceImpl"))

    // --- Menu Endpoints ---

    @PostMapping("/menus")
    // This PreAuthorize assumes restaurantSecurityServiceImpl is a bean named "restaurantSecurityServiceImpl"
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @restaurantSecurityServiceImpl.isRestaurantAdminForRestaurant(#createMenuRequestDto.restaurantId, principal.username))")
    @Operation(summary = "Create a new menu",
            description = "Creates a new menu for a specified restaurant. Requires ADMIN role or RESTAURANT_ADMIN role for the target restaurant.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Menu created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have necessary permissions",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Restaurant not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - Menu name already exists for the restaurant",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuResponseDto> createMenu(
            @Valid @RequestBody CreateMenuRequestDto createMenuRequestDto) {
        LOGGER.info("API call to create menu: {} for restaurant ID: {}", createMenuRequestDto.getName(), createMenuRequestDto.getRestaurantId());
        MenuResponseDto createdMenu = menuService.createMenu(createMenuRequestDto);
        LOGGER.info("Menu created with ID: {}", createdMenu.getId());
        return new ResponseEntity<>(createdMenu, HttpStatus.CREATED);
    }

    @GetMapping("/menus/{menuId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a menu by ID",
            description = "Retrieves details of a specific menu by its ID. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Menu found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuResponseDto> getMenuById(
            @Parameter(description = "ID of the menu to be retrieved") @PathVariable Long menuId) {
        LOGGER.debug("API call to get menu by ID: {}", menuId);
        MenuResponseDto menu = menuService.findMenuById(menuId);
        return ResponseEntity.ok(menu);
    }

    @GetMapping("/menus/by-restaurant/{restaurantId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all menus for a restaurant",
            description = "Retrieves a list of all menus for a specific restaurant. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of menus retrieved",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MenuResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
            })
    public ResponseEntity<List<MenuResponseDto>> getMenusByRestaurantId(
            @Parameter(description = "ID of the restaurant whose menus are to be retrieved") @PathVariable Long restaurantId) {
        LOGGER.debug("API call to get all menus for restaurant ID: {}", restaurantId);
        List<MenuResponseDto> menus = menuService.findMenusByRestaurantId(restaurantId);
        return ResponseEntity.ok(menus);
    }

    @GetMapping("/menus/by-restaurant/{restaurantId}/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active menus for a restaurant",
            description = "Retrieves a list of active menus for a specific restaurant. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of active menus retrieved",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MenuResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
            })
    public ResponseEntity<List<MenuResponseDto>> getActiveMenusByRestaurantId(
            @Parameter(description = "ID of the restaurant whose active menus are to be retrieved") @PathVariable Long restaurantId) {
        LOGGER.debug("API call to get active menus for restaurant ID: {}", restaurantId);
        List<MenuResponseDto> activeMenus = menuService.findActiveMenusByRestaurantId(restaurantId);
        return ResponseEntity.ok(activeMenus);
    }

    @PutMapping("/menus/{menuId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @menuSecurityServiceImpl.canManageMenu(#menuId, principal.username))")
    @Operation(summary = "Update an existing menu",
            description = "Updates details of an existing menu. Requires ADMIN role, or RESTAURANT_ADMIN role for the restaurant owning the menu.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Menu updated successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - Updated name already exists for the restaurant", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuResponseDto> updateMenu(
            @Parameter(description = "ID of the menu to be updated") @PathVariable Long menuId,
            @Valid @RequestBody UpdateMenuRequestDto updateMenuRequestDto) {
        LOGGER.info("API call to update menu with ID: {}", menuId);
        MenuResponseDto updatedMenu = menuService.updateMenu(menuId, updateMenuRequestDto);
        LOGGER.info("Menu with ID: {} updated successfully", updatedMenu.getId());
        return ResponseEntity.ok(updatedMenu);
    }

    @DeleteMapping("/menus/{menuId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @menuSecurityServiceImpl.canManageMenu(#menuId, principal.username))")
    @Operation(summary = "Delete a menu by ID (Soft Delete)",
            description = "Soft deletes a menu by its ID (sets isActive to false). Requires ADMIN role, or RESTAURANT_ADMIN role for the restaurant owning the menu.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Menu deleted successfully (set to inactive)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteMenu(
            @Parameter(description = "ID of the menu to be deleted") @PathVariable Long menuId) {
        LOGGER.info("API call to delete menu with ID: {}", menuId);
        menuService.deleteMenu(menuId);
        LOGGER.info("Menu with ID: {} soft deleted successfully", menuId);
        return ResponseEntity.noContent().build();
    }

    // --- MenuItem Endpoints ---

    @PostMapping("/menu-items") // Create item, menuId is in DTO
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @menuSecurityServiceImpl.canManageMenu(#createMenuItemRequestDto.menuId, principal.username))")
    @Operation(summary = "Add a new item to a menu",
            description = "Adds a new menu item to the specified menu. Requires ADMIN role or RESTAURANT_ADMIN for the parent menu's restaurant.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Menu item created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuItemResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., menuId not found, validation error)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Parent Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - Item name already exists in this menu", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuItemResponseDto> addMenuItemToMenu(
            @Valid @RequestBody CreateMenuItemRequestDto createMenuItemRequestDto) {
        LOGGER.info("API call to add menu item: {} to menu ID: {}", createMenuItemRequestDto.getName(), createMenuItemRequestDto.getMenuId());
        MenuItemResponseDto createdItem = menuService.addMenuItemToMenu(createMenuItemRequestDto);
        LOGGER.info("Menu item created with ID: {}", createdItem.getId());
        return new ResponseEntity<>(createdItem, HttpStatus.CREATED);
    }

    @GetMapping("/menu-items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a menu item by its ID",
            description = "Retrieves details of a specific menu item. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Menu item found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuItemResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu item not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuItemResponseDto> getMenuItemById(
            @Parameter(description = "ID of the menu item to retrieve") @PathVariable Long itemId) {
        LOGGER.debug("API call to get menu item by ID: {}", itemId);
        MenuItemResponseDto item = menuService.getMenuItemById(itemId);
        return ResponseEntity.ok(item);
    }

    @GetMapping("/menus/{menuId}/items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all items for a specific menu",
            description = "Retrieves all menu items associated with a given menu ID. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of menu items",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MenuItemResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<List<MenuItemResponseDto>> getMenuItemsByMenuId(
            @Parameter(description = "ID of the menu whose items are to be retrieved") @PathVariable Long menuId) {
        LOGGER.debug("API call to get menu items for menu ID: {}", menuId);
        // The service method should ideally check if the menu itself exists.
        List<MenuItemResponseDto> items = menuService.getMenuItemsByMenuId(menuId);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/menus/{menuId}/items/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active items for a specific menu",
            description = "Retrieves active menu items associated with a given menu ID. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of active menu items",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MenuItemResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<List<MenuItemResponseDto>> getActiveMenuItemsByMenuId(
            @Parameter(description = "ID of the menu whose active items are to be retrieved") @PathVariable Long menuId) {
        LOGGER.debug("API call to get active menu items for menu ID: {}", menuId);
        List<MenuItemResponseDto> items = menuService.getActiveMenuItemsByMenuId(menuId);
        return ResponseEntity.ok(items);
    }

    @PutMapping("/menu-items/{itemId}")
    // For updating an item, we need to check if the user can manage the item's parent menu.
    // This requires fetching the item, then its menu, then checking permission.
    // A custom SpEL function or a more direct check in the service might be cleaner.
    // For now, using a placeholder for a more complex authorization logic that would be implemented
    // in MenuSecurityService or directly in MenuService before update.
    // Example: @menuSecurityServiceImpl.canManageMenuItem(#itemId, principal.username)
    // Let's assume MenuService's updateMenuItem handles this authorization internally for now, or MenuSecurityService has canManageMenuItem.
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @menuSecurityServiceImpl.canManageMenuItem(#itemId, principal.username))")
    @Operation(summary = "Update a menu item",
            description = "Updates details of an existing menu item. Requires ADMIN role or RESTAURANT_ADMIN for the parent menu's restaurant.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Menu item updated successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuItemResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu item or parent menu not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Conflict - Item name already exists in this menu", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<MenuItemResponseDto> updateMenuItem(
            @Parameter(description = "ID of the menu item to update") @PathVariable Long itemId,
            @Valid @RequestBody UpdateMenuItemRequestDto updateMenuItemRequestDto) {
        LOGGER.info("API call to update menu item with ID: {}", itemId);
        // The MenuService.updateMenuItem should handle authorization by checking if the user can manage the item's parent menu.
        MenuItemResponseDto updatedItem = menuService.updateMenuItem(itemId, updateMenuItemRequestDto);
        LOGGER.info("Menu item with ID: {} updated successfully", updatedItem.getId());
        return ResponseEntity.ok(updatedItem);
    }

    @DeleteMapping("/menu-items/{itemId}")
    // Similar authorization to PUT: @menuSecurityServiceImpl.canManageMenuItem(#itemId, principal.username)
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @menuSecurityServiceImpl.canManageMenuItem(#itemId, principal.username))")
    @Operation(summary = "Delete a menu item (Soft Delete)",
            description = "Soft deletes a menu item by ID (sets isActive to false). Requires ADMIN role or RESTAURANT_ADMIN for the parent menu's restaurant.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Menu item deleted successfully (set to inactive)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Menu item not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteMenuItem(
            @Parameter(description = "ID of the menu item to be deleted") @PathVariable Long itemId) {
        LOGGER.info("API call to delete menu item with ID: {}", itemId);
        // The MenuService.deleteMenuItem should handle authorization.
        menuService.deleteMenuItem(itemId);
        LOGGER.info("Menu item with ID: {} soft deleted successfully", itemId);
        return ResponseEntity.noContent().build();
    }
}
