// File: backend/restaurant/src/main/java/com/multirestaurantplatform/restaurant/service/RestaurantServiceImpl.java
package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.common.exception.BadRequestException;
import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role; // Import Role
import com.multirestaurantplatform.security.model.User;   // Import User
import com.multirestaurantplatform.security.repository.UserRepository; // Import UserRepository

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor // Lombok for constructor injection
public class RestaurantServiceImpl implements RestaurantService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestaurantServiceImpl.class);

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository; // Added UserRepository

    @Override
    @Transactional
    public Restaurant createRestaurant(CreateRestaurantRequestDto createDto) {
        LOGGER.info("Attempting to create restaurant with name: {}", createDto.getName());

        restaurantRepository.findByName(createDto.getName()).ifPresent(r -> {
            LOGGER.warn("Restaurant creation failed: name '{}' already exists.", createDto.getName());
            throw new ConflictException("Restaurant with name '" + createDto.getName() + "' already exists.");
        });

        if (StringUtils.hasText(createDto.getEmail())) {
            restaurantRepository.findByEmail(createDto.getEmail()).ifPresent(r -> {
                LOGGER.warn("Restaurant creation failed: email '{}' already exists.", createDto.getEmail());
                throw new ConflictException("Restaurant with email '" + createDto.getEmail() + "' already exists.");
            });
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName(createDto.getName());
        restaurant.setDescription(createDto.getDescription());
        restaurant.setAddress(createDto.getAddress());
        restaurant.setPhoneNumber(createDto.getPhoneNumber());
        restaurant.setEmail(createDto.getEmail());
        restaurant.setActive(true);
        // Initially, no admins are assigned via create. Admins are set via update.
        restaurant.setRestaurantAdmins(new HashSet<>());

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        LOGGER.info("Restaurant created successfully with ID: {}", savedRestaurant.getId());
        return savedRestaurant;
    }

    @Override
    @Transactional(readOnly = true)
    public Restaurant findRestaurantById(Long id) {
        LOGGER.debug("Attempting to find restaurant with ID: {}", id);
        return restaurantRepository.findById(id)
                .orElseThrow(() -> {
                    LOGGER.warn("Restaurant not found with ID: {}", id);
                    return new ResourceNotFoundException("Restaurant not found with ID: " + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Restaurant> findAllRestaurants() {
        LOGGER.debug("Fetching all restaurants");
        return restaurantRepository.findAll();
    }

    @Override
    @Transactional
    public Restaurant updateRestaurant(Long id, UpdateRestaurantRequestDto updateDto) {
        LOGGER.info("Attempting to update restaurant with ID: {}", id);
        Restaurant restaurant = findRestaurantById(id); // Throws ResourceNotFoundException if not found

        // Update basic fields
        if (updateDto.getName() != null) {
            String newName = updateDto.getName();
            if (!newName.equals(restaurant.getName())) {
                restaurantRepository.findByName(newName).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        LOGGER.warn("Restaurant update failed for ID {}: name '{}' already exists for restaurant ID {}.", id, newName, existing.getId());
                        throw new ConflictException("Restaurant with name '" + newName + "' already exists.");
                    }
                });
                restaurant.setName(newName);
            }
        }

        if (updateDto.getEmail() != null) {
            String newEmail = updateDto.getEmail();
            if (StringUtils.hasText(newEmail) && !newEmail.equals(restaurant.getEmail())) {
                restaurantRepository.findByEmail(newEmail).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        LOGGER.warn("Restaurant update failed for ID {}: email '{}' already exists for restaurant ID {}.", id, newEmail, existing.getId());
                        throw new ConflictException("Restaurant with email '" + newEmail + "' already exists.");
                    }
                });
                restaurant.setEmail(newEmail);
            } else if (updateDto.getEmail().isEmpty() && restaurant.getEmail() != null) {
                restaurant.setEmail(null);
            }
        }

        if (updateDto.getDescription() != null) {
            restaurant.setDescription(updateDto.getDescription());
        }
        if (updateDto.getAddress() != null) {
            restaurant.setAddress(updateDto.getAddress());
        }
        if (updateDto.getPhoneNumber() != null) {
            restaurant.setPhoneNumber(updateDto.getPhoneNumber());
        }
        if (updateDto.getIsActive() != null) {
            restaurant.setActive(updateDto.getIsActive());
        }

        // --- Handle Restaurant Admin Assignment ---
        if (updateDto.getAdminUserIds() != null) {
            LOGGER.info("Updating administrators for restaurant ID: {}. Provided adminUserIds: {}", id, updateDto.getAdminUserIds());
            Set<User> newAdmins = new HashSet<>();
            if (!updateDto.getAdminUserIds().isEmpty()) {
                for (Long adminUserId : updateDto.getAdminUserIds()) {
                    User potentialAdmin = userRepository.findById(adminUserId)
                            .orElseThrow(() -> {
                                LOGGER.warn("User with ID {} not found while assigning admins to restaurant ID {}", adminUserId, id);
                                return new ResourceNotFoundException("User not found with ID: " + adminUserId + " for admin assignment.");
                            });

                    if (!potentialAdmin.getRoles().contains(Role.RESTAURANT_ADMIN)) {
                        LOGGER.warn("User with ID {} (username: {}) does not have RESTAURANT_ADMIN role. Cannot assign as admin to restaurant ID {}",
                                adminUserId, potentialAdmin.getUsername(), id);
                        throw new BadRequestException("User " + potentialAdmin.getUsername() + " (ID: " + adminUserId + ") is not a RESTAURANT_ADMIN and cannot be assigned to manage a restaurant.");
                    }
                    newAdmins.add(potentialAdmin);
                    LOGGER.debug("User {} (ID: {}) added as potential admin for restaurant ID {}", potentialAdmin.getUsername(), adminUserId, id);
                }
            }
            // Replace the existing set of admins with the new set
            restaurant.getRestaurantAdmins().clear(); // Clear existing admins first
            restaurant.getRestaurantAdmins().addAll(newAdmins); // Add new admins
            LOGGER.info("Successfully processed adminUserIds for restaurant ID: {}. New admin count: {}", id, newAdmins.size());
        }
        // If updateDto.getAdminUserIds() is null, the existing admins are not modified.

        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        LOGGER.info("Restaurant with ID: {} updated successfully.", id);
        return updatedRestaurant;
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long id) {
        LOGGER.info("Attempting to delete restaurant with ID: {}", id);
        if (!restaurantRepository.existsById(id)) {
            LOGGER.warn("Restaurant deletion failed: not found with ID: {}", id);
            throw new ResourceNotFoundException("Restaurant not found with ID: " + id + " for deletion.");
        }
        // Before deleting a restaurant, consider implications for related entities (e.g., menus, orders).
        // Depending on cascading rules or business logic, you might need to handle these explicitly.
        // For example, disassociating or deleting menus. For now, we assume cascading delete or manual cleanup.
        restaurantRepository.deleteById(id);
        LOGGER.info("Restaurant with ID: {} deleted successfully.", id);
    }
}
