package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor // Lombok for constructor injection
public class RestaurantServiceImpl implements RestaurantService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestaurantServiceImpl.class);

    private final RestaurantRepository restaurantRepository;
    // We might need UserRepository later if we implement admin assignment logic here

    @Override
    @Transactional
    public Restaurant createRestaurant(CreateRestaurantRequestDto createDto) {
        LOGGER.info("Attempting to create restaurant with name: {}", createDto.getName());

        // Check for existing restaurant by name
        restaurantRepository.findByName(createDto.getName()).ifPresent(r -> {
            LOGGER.warn("Restaurant creation failed: name '{}' already exists.", createDto.getName());
            throw new ConflictException("Restaurant with name '" + createDto.getName() + "' already exists.");
        });

        // Check for existing restaurant by email (if email is intended to be unique across restaurants)
        if (createDto.getEmail() != null && !createDto.getEmail().isEmpty()) {
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
        restaurant.setActive(true); // Default to active on creation

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
        // Later: Add Pageable for pagination: public Page<Restaurant> findAllRestaurants(Pageable pageable)
    }

    @Override
    @Transactional
    public Restaurant updateRestaurant(Long id, UpdateRestaurantRequestDto updateDto) {
        LOGGER.info("Attempting to update restaurant with ID: {}", id);
        Restaurant restaurant = findRestaurantById(id); // Throws ResourceNotFoundException if not found

        updateDto.getName().ifPresent(name -> {
            if (!name.equals(restaurant.getName())) { // Only check if name is actually changing
                restaurantRepository.findByName(name).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) { // If the found restaurant is not the current one
                        LOGGER.warn("Restaurant update failed for ID {}: name '{}' already exists for restaurant ID {}.", id, name, existing.getId());
                        throw new ConflictException("Restaurant with name '" + name + "' already exists.");
                    }
                });
                restaurant.setName(name);
            }
        });

        updateDto.getEmail().ifPresent(email -> {
            if (email != null && !email.isEmpty() && (restaurant.getEmail() == null || !email.equals(restaurant.getEmail()))) {
                 restaurantRepository.findByEmail(email).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        LOGGER.warn("Restaurant update failed for ID {}: email '{}' already exists for restaurant ID {}.", id, email, existing.getId());
                        throw new ConflictException("Restaurant with email '" + email + "' already exists.");
                    }
                });
                restaurant.setEmail(email);
            } else if (email != null && email.isEmpty()) { // Allow clearing email if business logic permits
                 restaurant.setEmail(null);
            }
        });


        updateDto.getDescription().ifPresent(restaurant::setDescription);
        updateDto.getAddress().ifPresent(restaurant::setAddress);
        updateDto.getPhoneNumber().ifPresent(restaurant::setPhoneNumber);
        updateDto.getIsActive().ifPresent(restaurant::setActive);

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
        restaurantRepository.deleteById(id);
        LOGGER.info("Restaurant with ID: {} deleted successfully.", id);
        // If implementing soft delete:
        // Restaurant restaurant = findRestaurantById(id);
        // restaurant.setActive(false);
        // restaurantRepository.save(restaurant);
    }
}