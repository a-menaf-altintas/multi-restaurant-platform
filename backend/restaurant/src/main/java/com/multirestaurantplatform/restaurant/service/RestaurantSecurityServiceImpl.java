package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role; // Import Role
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service("restaurantSecurityServiceImpl") // Explicit bean name to match SpEL expression
@RequiredArgsConstructor
public class RestaurantSecurityServiceImpl implements RestaurantSecurityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestaurantSecurityServiceImpl.class);

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Ensures the session is active for lazy loading if needed
    public boolean isRestaurantAdminForRestaurant(Long restaurantId, String username) {
        LOGGER.debug("SecurityCheck: Checking if user '{}' is admin for restaurant ID '{}'", username, restaurantId);

        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            LOGGER.warn("SecurityCheck: User '{}' not found.", username);
            return false; // User not found, so cannot be an admin
        }
        User user = userOptional.get();

        // Ensure the user actually has the RESTAURANT_ADMIN role.
        // While the @PreAuthorize might check hasRole('RESTAURANT_ADMIN') first,
        // it's good practice for this specific service method to also be mindful of the role
        // or assume the caller (SpEL) has already filtered by role.
        // For robustness, we can add a check here, though SpEL usually handles the role part.
        boolean isRestaurantAdminRole = user.getRoles().stream()
                .anyMatch(role -> role == Role.RESTAURANT_ADMIN);
        if (!isRestaurantAdminRole) {
            LOGGER.debug("SecurityCheck: User '{}' does not have RESTAURANT_ADMIN role.", username);
            // If the SpEL is "(hasRole('RESTAURANT_ADMIN') AND @bean.method())",
            // this check might be redundant but harmless.
            // If SpEL was just "@bean.method()", this check would be critical.
            return false;
        }


        Optional<Restaurant> restaurantOptional = restaurantRepository.findById(restaurantId);
        if (restaurantOptional.isEmpty()) {
            LOGGER.warn("SecurityCheck: Restaurant ID '{}' not found.", restaurantId);
            return false; // Restaurant not found
        }
        Restaurant restaurant = restaurantOptional.get();

        // Hibernate will lazy-load restaurant.getRestaurantAdmins() if the session is active.
        // The @Transactional annotation ensures this.
        // We check if the user's ID is present in the set of admin IDs for the restaurant.
        boolean isUserAdminForThisRestaurant = restaurant.getRestaurantAdmins().stream()
                .anyMatch(adminUser -> adminUser.getId().equals(user.getId()));

        if (isUserAdminForThisRestaurant) {
            LOGGER.info("SecurityCheck: User '{}' IS an admin for restaurant ID '{}'. Access granted by this check.", username, restaurantId);
        } else {
            LOGGER.info("SecurityCheck: User '{}' is NOT an admin for restaurant ID '{}'. Access denied by this check.", username, restaurantId);
        }

        return isUserAdminForThisRestaurant;
    }
}
