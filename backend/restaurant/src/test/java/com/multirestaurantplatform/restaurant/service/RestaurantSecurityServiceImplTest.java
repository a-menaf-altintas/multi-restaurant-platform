package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantSecurityServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    private RestaurantSecurityServiceImpl restaurantSecurityService;

    @BeforeEach
    void setUp() {
        restaurantSecurityService = new RestaurantSecurityServiceImpl(restaurantRepository, userRepository);
    }

    @Test
    void isRestaurantAdminForRestaurant_UserNotFound_ReturnsFalse() {
        // Arrange
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // Act
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(1L, "nonexistent-user");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("nonexistent-user");
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void isRestaurantAdminForRestaurant_UserNotRestaurantAdmin_ReturnsFalse() {
        // Arrange
        User customerUser = new User();
        customerUser.setId(1L);
        customerUser.setUsername("customer");
        customerUser.setRoles(Set.of(Role.CUSTOMER)); // Not a RESTAURANT_ADMIN

        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customerUser));

        // Act
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(1L, "customer");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("customer");
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void isRestaurantAdminForRestaurant_RestaurantNotFound_ReturnsFalse() {
        // Arrange
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(restaurantRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(999L, "admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("admin");
        verify(restaurantRepository).findById(999L);
    }

    @Test
    void isRestaurantAdminForRestaurant_UserIsAdminForRestaurant_ReturnsTrue() {
        // Arrange
        Long restaurantId = 1L;
        Long userId = 101L;

        // Create admin user with RESTAURANT_ADMIN role
        User adminUser = new User();
        adminUser.setId(userId);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        // Create restaurant with the admin user as a restaurant admin
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Test Restaurant");
        Set<User> restaurantAdmins = new HashSet<>();
        restaurantAdmins.add(adminUser);
        restaurant.setRestaurantAdmins(restaurantAdmins);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // Act
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(restaurantId, "admin");

        // Assert
        assertTrue(result);
        verify(userRepository).findByUsername("admin");
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void isRestaurantAdminForRestaurant_UserIsNotAdminForRestaurant_ReturnsFalse() {
        // Arrange
        Long restaurantId = 1L;

        // Create admin user with RESTAURANT_ADMIN role
        User adminUser = new User();
        adminUser.setId(101L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        // Create a different admin user
        User otherAdmin = new User();
        otherAdmin.setId(102L);
        otherAdmin.setUsername("other-admin");
        otherAdmin.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        // Create restaurant with the OTHER admin as a restaurant admin (not our test admin)
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Test Restaurant");
        Set<User> restaurantAdmins = new HashSet<>();
        restaurantAdmins.add(otherAdmin);
        restaurant.setRestaurantAdmins(restaurantAdmins);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // Act
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(restaurantId, "admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("admin");
        verify(restaurantRepository).findById(restaurantId);
    }

    @Test
    void isRestaurantAdminForRestaurant_PlatformAdmin_StillReturnsFalse() {
        // Arrange
        // Even if a user has ADMIN role, they still need to be explicitly added to restaurant admins
        User platformAdmin = new User();
        platformAdmin.setId(1L);
        platformAdmin.setUsername("platform-admin");
        platformAdmin.setRoles(Set.of(Role.ADMIN)); // Platform admin, not restaurant admin

        when(userRepository.findByUsername("platform-admin")).thenReturn(Optional.of(platformAdmin));

        // No need to stub restaurantRepository.findById() since the code shouldn't reach that point
        // Removing the unnecessary stubbing that was causing the error

        // Act - Even though user is platform admin, the method specifically checks for restaurant admin role
        boolean result = restaurantSecurityService.isRestaurantAdminForRestaurant(1L, "platform-admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("platform-admin");
        // Restaurant is never fetched since user role check failed
        verify(restaurantRepository, never()).findById(anyLong());
    }
}