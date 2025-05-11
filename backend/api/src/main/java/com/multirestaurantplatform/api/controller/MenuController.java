// File: backend/api/src/main/java/com/multirestaurantplatform/api/controller/MenuController.java
package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.service.MenuService;
// Import MenuSecurityService if you need to inject its bean, though for SpEL it's usually not directly injected here
// import com.multirestaurantplatform.menu.service.MenuSecurityService;
// Import RestaurantSecurityService if you need to inject its bean for SpEL usage
// import com.multirestaurantplatform.restaurant.service.RestaurantSecurityService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "Menu Management", description = "APIs for managing restaurant menus")
@SecurityRequirement(name = "bearerAuth") // Indicates JWT is generally required
public class MenuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);
    private final MenuService menuService;
    // No need to inject security services here if they are only used in @PreAuthorize SpEL expressions
    // and are correctly named Spring beans (e.g., @Service("menuSecurityServiceImpl"))

    @PostMapping
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

    @GetMapping("/{menuId}")
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

    @GetMapping("/by-restaurant/{restaurantId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all menus for a restaurant",
            description = "Retrieves a list of all menus for a specific restaurant. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of menus retrieved",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuResponseDto.class))), // Assuming list response
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
            })
    public ResponseEntity<List<MenuResponseDto>> getMenusByRestaurantId(
            @Parameter(description = "ID of the restaurant whose menus are to be retrieved") @PathVariable Long restaurantId) {
        LOGGER.debug("API call to get all menus for restaurant ID: {}", restaurantId);
        List<MenuResponseDto> menus = menuService.findMenusByRestaurantId(restaurantId);
        return ResponseEntity.ok(menus);
    }

    @GetMapping("/by-restaurant/{restaurantId}/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active menus for a restaurant",
            description = "Retrieves a list of active menus for a specific restaurant. Accessible by any authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of active menus retrieved",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MenuResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.multirestaurantplatform.api.dto.error.ErrorResponse.class))),
            })
    public ResponseEntity<List<MenuResponseDto>> getActiveMenusByRestaurantId(
            @Parameter(description = "ID of the restaurant whose active menus are to be retrieved") @PathVariable Long restaurantId) {
        LOGGER.debug("API call to get active menus for restaurant ID: {}", restaurantId);
        List<MenuResponseDto> activeMenus = menuService.findActiveMenusByRestaurantId(restaurantId);
        return ResponseEntity.ok(activeMenus);
    }

    @PutMapping("/{menuId}")
    // Updated PreAuthorize to use menuSecurityServiceImpl
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

    @DeleteMapping("/{menuId}")
    // Updated PreAuthorize to use menuSecurityServiceImpl
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
}
