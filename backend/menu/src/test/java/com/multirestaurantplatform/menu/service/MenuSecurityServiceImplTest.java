package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.service.RestaurantSecurityService;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuSecurityServiceImplTest {

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantSecurityService restaurantSecurityService;

    private MenuSecurityServiceImpl menuSecurityService;

    @BeforeEach
    void setUp() {
        menuSecurityService = new MenuSecurityServiceImpl(menuRepository, userRepository, restaurantSecurityService);
    }

    @Test
    void canManageMenu_UserNotFound_ReturnsFalse() {
        // Arrange
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // Act
        boolean result = menuSecurityService.canManageMenu(1L, "nonexistent-user");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("nonexistent-user");
        verifyNoInteractions(menuRepository);
        verifyNoInteractions(restaurantSecurityService);
    }

    @Test
    void canManageMenu_UserNotRestaurantAdmin_ReturnsFalse() {
        // Arrange
        User customerUser = new User();
        customerUser.setId(1L);
        customerUser.setUsername("customer");
        customerUser.setRoles(Set.of(Role.CUSTOMER)); // Not a RESTAURANT_ADMIN

        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(customerUser));

        // Act
        boolean result = menuSecurityService.canManageMenu(1L, "customer");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("customer");
        verifyNoInteractions(menuRepository);
        verifyNoInteractions(restaurantSecurityService);
    }

    @Test
    void canManageMenu_MenuNotFound_ReturnsFalse() {
        // Arrange
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(menuRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        boolean result = menuSecurityService.canManageMenu(999L, "admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("admin");
        verify(menuRepository).findById(999L);
        verifyNoInteractions(restaurantSecurityService);
    }

    @Test
    void canManageMenu_MenuWithNullRestaurant_ReturnsFalse() {
        // Arrange
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        Menu menu = new Menu();
        menu.setId(1L);
        menu.setName("Test Menu");
        menu.setRestaurant(null); // Null restaurant

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(menuRepository.findById(1L)).thenReturn(Optional.of(menu));

        // Act
        boolean result = menuSecurityService.canManageMenu(1L, "admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("admin");
        verify(menuRepository).findById(1L);
        verifyNoInteractions(restaurantSecurityService);
    }

    @Test
    void canManageMenu_UserIsAdminForRestaurant_ReturnsTrue() {
        // Arrange
        Long menuId = 1L;
        Long restaurantId = 100L;

        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Test Restaurant");

        Menu menu = new Menu();
        menu.setId(menuId);
        menu.setName("Test Menu");
        menu.setRestaurant(restaurant);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(restaurantSecurityService.isRestaurantAdminForRestaurant(restaurantId, "admin")).thenReturn(true);

        // Act
        boolean result = menuSecurityService.canManageMenu(menuId, "admin");

        // Assert
        assertTrue(result);
        verify(userRepository).findByUsername("admin");
        verify(menuRepository).findById(menuId);
        verify(restaurantSecurityService).isRestaurantAdminForRestaurant(restaurantId, "admin");
    }

    @Test
    void canManageMenu_UserIsNotAdminForRestaurant_ReturnsFalse() {
        // Arrange
        Long menuId = 1L;
        Long restaurantId = 100L;

        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Test Restaurant");

        Menu menu = new Menu();
        menu.setId(menuId);
        menu.setName("Test Menu");
        menu.setRestaurant(restaurant);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(menu));
        when(restaurantSecurityService.isRestaurantAdminForRestaurant(restaurantId, "admin")).thenReturn(false);

        // Act
        boolean result = menuSecurityService.canManageMenu(menuId, "admin");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("admin");
        verify(menuRepository).findById(menuId);
        verify(restaurantSecurityService).isRestaurantAdminForRestaurant(restaurantId, "admin");
    }
}