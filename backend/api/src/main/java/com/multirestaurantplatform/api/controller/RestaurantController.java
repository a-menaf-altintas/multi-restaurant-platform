package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.common.dto.error.ErrorResponse;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.service.RestaurantService;
// Import your custom security service if you create one for method-level checks
// import com.multirestaurantplatform.security.service.RestaurantSecurityService;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant Management", description = "APIs for managing restaurants")
@SecurityRequirement(name = "bearerAuth") // Indicates that JWT is required for these endpoints by default
public class RestaurantController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestaurantController.class);
    private final RestaurantService restaurantService;
    // Uncomment if you implement RestaurantSecurityService for custom authorization
    // private final RestaurantSecurityService restaurantSecurityService;


    // Helper method to map Restaurant Entity to RestaurantResponseDto
    private RestaurantResponseDto mapToRestaurantResponseDto(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }
        return new RestaurantResponseDto(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getAddress(),
                restaurant.getPhoneNumber(),
                restaurant.getEmail(),
                restaurant.isActive(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new restaurant",
               description = "Creates a new restaurant. Requires ADMIN role.",
               responses = {
                   @ApiResponse(responseCode = "201", description = "Restaurant created successfully",
                                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantResponseDto.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., validation error)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "409", description = "Conflict - Restaurant name or email already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
               })
    public ResponseEntity<RestaurantResponseDto> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequestDto createDto) {
        LOGGER.info("API call to create restaurant with name: {}", createDto.getName());
        Restaurant createdRestaurant = restaurantService.createRestaurant(createDto);
        LOGGER.info("Restaurant created with ID: {}", createdRestaurant.getId());
        return new ResponseEntity<>(mapToRestaurantResponseDto(createdRestaurant), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // Allows any authenticated user to view restaurant details. Adjust if needed.
    @Operation(summary = "Get a restaurant by ID",
               description = "Retrieves details of a specific restaurant by its ID. Accessible by any authenticated user.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Restaurant found",
                                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantResponseDto.class))),
                   @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "403", description = "Forbidden (if specific role checks were added and failed)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
               })
    public ResponseEntity<RestaurantResponseDto> getRestaurantById(@PathVariable Long id) {
        LOGGER.debug("API call to get restaurant by ID: {}", id);
        Restaurant restaurant = restaurantService.findRestaurantById(id);
        return ResponseEntity.ok(mapToRestaurantResponseDto(restaurant));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()") // Allows any authenticated user to list restaurants.
    @Operation(summary = "Get all restaurants",
               description = "Retrieves a list of all restaurants. Accessible by any authenticated user. (Pagination to be added later)",
               responses = {
                   @ApiResponse(responseCode = "200", description = "List of restaurants retrieved",
                                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantResponseDto.class))),
                   @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
               })
    public ResponseEntity<List<RestaurantResponseDto>> getAllRestaurants() {
        // TODO: Implement pagination (e.g., using Pageable) and filtering
        LOGGER.debug("API call to get all restaurants");
        List<Restaurant> restaurants = restaurantService.findAllRestaurants();
        List<RestaurantResponseDto> responseDtos = restaurants.stream()
                .map(this::mapToRestaurantResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @PutMapping("/{id}")
    // Option 1: Simple ADMIN only
    // @PreAuthorize("hasRole('ADMIN')")
    // Option 2: ADMIN or specific RESTAURANT_ADMIN (requires custom RestaurantSecurityService)
    @PreAuthorize("hasRole('ADMIN') or (hasRole('RESTAURANT_ADMIN') and @restaurantSecurityServiceImpl.isRestaurantAdminForRestaurant(#id, principal.username))")
    @Operation(summary = "Update an existing restaurant",
               description = "Updates details of an existing restaurant. Requires ADMIN role, or RESTAURANT_ADMIN role for the specific restaurant if `RestaurantSecurityService` is implemented.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Restaurant updated successfully",
                                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RestaurantResponseDto.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "409", description = "Conflict - Updated name or email already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
               })
    public ResponseEntity<RestaurantResponseDto> updateRestaurant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantRequestDto updateDto) {
        LOGGER.info("API call to update restaurant with ID: {}", id);
        Restaurant updatedRestaurant = restaurantService.updateRestaurant(id, updateDto);
        LOGGER.info("Restaurant with ID: {} updated successfully", updatedRestaurant.getId());
        return ResponseEntity.ok(mapToRestaurantResponseDto(updatedRestaurant));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a restaurant by ID",
               description = "Deletes a specific restaurant by its ID. Requires ADMIN role.",
               responses = {
                   @ApiResponse(responseCode = "204", description = "Restaurant deleted successfully"),
                   @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
               })
    public ResponseEntity<Void> deleteRestaurant(@PathVariable Long id) {
        LOGGER.info("API call to delete restaurant with ID: {}", id);
        restaurantService.deleteRestaurant(id);
        LOGGER.info("Restaurant with ID: {} deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}
