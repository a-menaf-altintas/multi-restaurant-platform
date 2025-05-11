// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/service/MenuSecurityServiceImpl.java
package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.service.RestaurantSecurityService; // Assuming this is the correct interface
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service("menuSecurityServiceImpl") // Explicit bean name for SpEL expression in @PreAuthorize
@RequiredArgsConstructor
public class MenuSecurityServiceImpl implements MenuSecurityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuSecurityServiceImpl.class);

    private final MenuRepository menuRepository;
    private final UserRepository userRepository; // To fetch user roles
    private final RestaurantSecurityService restaurantSecurityService; // To check if user is admin for the restaurant

    @Override
    @Transactional(readOnly = true) // Read-only as it's a check operation
    public boolean canManageMenu(Long menuId, String username) {
        LOGGER.debug("SecurityCheck: Checking if user '{}' can manage menu ID '{}'", username, menuId);

        // 1. Fetch the user to check their roles
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            LOGGER.warn("SecurityCheck: User '{}' not found. Cannot manage menu.", username);
            return false;
        }
        User user = userOptional.get();

        // 2. Ensure the user has the RESTAURANT_ADMIN role.
        //    While @PreAuthorize("hasRole('RESTAURANT_ADMIN') and @bean.method()") handles the role check,
        //    it's good for this service to be aware or for standalone use.
        boolean isRestaurantAdminRole = user.getRoles().stream()
                .anyMatch(role -> role == Role.RESTAURANT_ADMIN);

        if (!isRestaurantAdminRole) {
            LOGGER.debug("SecurityCheck: User '{}' does not have RESTAURANT_ADMIN role. Cannot manage menu.", username);
            return false; // If not a RESTAURANT_ADMIN, they can't manage via this specific check.
                          // ADMIN role would be handled by a separate "hasRole('ADMIN')" in @PreAuthorize.
        }

        // 3. Fetch the menu
        Optional<Menu> menuOptional = menuRepository.findById(menuId);
        if (menuOptional.isEmpty()) {
            LOGGER.warn("SecurityCheck: Menu ID '{}' not found. Cannot determine management permission.", menuId);
            // If menu doesn't exist, access is implicitly denied for management.
            // The controller will likely throw a 404 separately.
            return false;
        }
        Menu menu = menuOptional.get();

        // 4. Get the restaurant associated with the menu
        Restaurant restaurant = menu.getRestaurant();
        if (restaurant == null) {
            LOGGER.error("SecurityCheck: Menu ID '{}' is not associated with any restaurant. Data integrity issue?", menuId);
            return false; // Should not happen in a consistent DB state
        }
        Long restaurantId = restaurant.getId();

        // 5. Delegate to RestaurantSecurityService to check if the user is an admin for this specific restaurant
        boolean isUserAdminForRestaurant = restaurantSecurityService.isRestaurantAdminForRestaurant(restaurantId, username);

        if (isUserAdminForRestaurant) {
            LOGGER.info("SecurityCheck: User '{}' IS an admin for restaurant ID '{}' (owner of menu ID '{}'). Access GRANTED by this check.", username, restaurantId, menuId);
        } else {
            LOGGER.info("SecurityCheck: User '{}' is NOT an admin for restaurant ID '{}' (owner of menu ID '{}'). Access DENIED by this check.", username, restaurantId, menuId);
        }

        return isUserAdminForRestaurant;
    }
}
