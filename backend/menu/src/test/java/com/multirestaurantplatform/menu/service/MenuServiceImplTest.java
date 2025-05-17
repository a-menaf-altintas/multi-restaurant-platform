// File: backend/menu/src/test/java/com/multirestaurantplatform/menu/service/MenuServiceImplTest.java
package com.multirestaurantplatform.menu.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.model.MenuItem; // Import MenuItem
import com.multirestaurantplatform.menu.repository.MenuItemRepository; // Import MenuItemRepository
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock // Added mock for MenuItemRepository
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuServiceImpl menuService;

    private Restaurant testRestaurant;
    private Menu testMenu;
    private CreateMenuRequestDto createMenuRequestDto;
    private UpdateMenuRequestDto updateMenuRequestDto;

    @BeforeEach
    void setUp() {
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setName("Test Restaurant");

        testMenu = new Menu();
        testMenu.setId(10L);
        testMenu.setName("Lunch Menu");
        testMenu.setDescription("Delicious lunch options");
        testMenu.setActive(true);
        testMenu.setRestaurant(testRestaurant);
        testMenu.setCreatedAt(Instant.now());
        testMenu.setUpdatedAt(Instant.now());

        createMenuRequestDto = new CreateMenuRequestDto();
        createMenuRequestDto.setName("Dinner Menu");
        createMenuRequestDto.setDescription("Evening specials");
        createMenuRequestDto.setRestaurantId(testRestaurant.getId());

        updateMenuRequestDto = new UpdateMenuRequestDto();
    }

    // --- Test cases for createMenu ---

    @Test
    @DisplayName("createMenu - Success")
    void createMenu_whenValidRequest_shouldReturnCreatedMenuResponseDto() {
        // Arrange
        when(restaurantRepository.findById(createMenuRequestDto.getRestaurantId())).thenReturn(Optional.of(testRestaurant));
        when(menuRepository.findByRestaurantIdAndNameIgnoreCase(anyLong(), anyString())).thenReturn(Optional.empty());
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu savedMenu = invocation.getArgument(0);
            savedMenu.setId(11L);
            savedMenu.setCreatedAt(Instant.now());
            savedMenu.setUpdatedAt(Instant.now());
            savedMenu.setRestaurant(testRestaurant);
            return savedMenu;
        });

        // Act
        MenuResponseDto result = menuService.createMenu(createMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(createMenuRequestDto.getName(), result.getName());
        assertEquals(createMenuRequestDto.getDescription(), result.getDescription());
        assertEquals(createMenuRequestDto.getRestaurantId(), result.getRestaurantId());
        assertTrue(result.isActive());
        assertEquals(11L, result.getId());
        verify(restaurantRepository).findById(createMenuRequestDto.getRestaurantId());
        verify(menuRepository).findByRestaurantIdAndNameIgnoreCase(createMenuRequestDto.getRestaurantId(), createMenuRequestDto.getName());
        verify(menuRepository).save(any(Menu.class));
    }

    @Test
    @DisplayName("createMenu - Restaurant Not Found")
    void createMenu_whenRestaurantNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(restaurantRepository.findById(createMenuRequestDto.getRestaurantId())).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> menuService.createMenu(createMenuRequestDto));
        assertTrue(exception.getMessage().contains("Restaurant not found with ID: " + createMenuRequestDto.getRestaurantId()));
        verify(restaurantRepository).findById(createMenuRequestDto.getRestaurantId());
        verify(menuRepository, never()).findByRestaurantIdAndNameIgnoreCase(anyLong(), anyString());
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("createMenu - Menu Name Conflict")
    void createMenu_whenMenuNameExistsForRestaurant_shouldThrowConflictException() {
        // Arrange
        when(restaurantRepository.findById(createMenuRequestDto.getRestaurantId())).thenReturn(Optional.of(testRestaurant));
        Menu existingMenuWithSameName = new Menu();
        existingMenuWithSameName.setId(12L);
        existingMenuWithSameName.setName(createMenuRequestDto.getName());
        existingMenuWithSameName.setRestaurant(testRestaurant);

        when(menuRepository.findByRestaurantIdAndNameIgnoreCase(createMenuRequestDto.getRestaurantId(), createMenuRequestDto.getName()))
                .thenReturn(Optional.of(existingMenuWithSameName));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> menuService.createMenu(createMenuRequestDto));
        assertTrue(exception.getMessage().contains("Menu with name '" + createMenuRequestDto.getName() + "' already exists for this restaurant."));
        verify(restaurantRepository).findById(createMenuRequestDto.getRestaurantId());
        verify(menuRepository).findByRestaurantIdAndNameIgnoreCase(createMenuRequestDto.getRestaurantId(), createMenuRequestDto.getName());
        verify(menuRepository, never()).save(any(Menu.class));
    }

    // --- Test cases for findMenuById ---

    @Test
    @DisplayName("findMenuById - Success")
    void findMenuById_whenMenuExists_shouldReturnMenuResponseDto() {
        // Arrange
        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));

        // Act
        MenuResponseDto result = menuService.findMenuById(testMenu.getId());

        // Assert
        assertNotNull(result);
        assertEquals(testMenu.getId(), result.getId());
        assertEquals(testMenu.getName(), result.getName());
        assertEquals(testRestaurant.getId(), result.getRestaurantId());
        verify(menuRepository).findById(testMenu.getId());
    }

    @Test
    @DisplayName("findMenuById - Not Found")
    void findMenuById_whenMenuDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistentMenuId = 99L;
        when(menuRepository.findById(nonExistentMenuId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> menuService.findMenuById(nonExistentMenuId));
        assertEquals("Menu not found with ID: " + nonExistentMenuId, exception.getMessage());
        verify(menuRepository).findById(nonExistentMenuId);
    }

    @Test
    @DisplayName("findMenuById - Success with Null Restaurant in Menu (Mapper Test)")
    void findMenuById_whenMenuHasNullRestaurant_mapsRestaurantIdToNull() {
        // Arrange
        testMenu.setRestaurant(null);
        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));

        // Act
        MenuResponseDto result = menuService.findMenuById(testMenu.getId());

        // Assert
        assertNotNull(result);
        assertEquals(testMenu.getId(), result.getId());
        assertNull(result.getRestaurantId());
        verify(menuRepository).findById(testMenu.getId());
    }


    // --- Test cases for findMenusByRestaurantId ---

    @Test
    @DisplayName("findMenusByRestaurantId - Success with Menus")
    void findMenusByRestaurantId_whenRestaurantHasMenus_shouldReturnListOfMenuResponseDto() {
        // Arrange
        when(menuRepository.findByRestaurantId(testRestaurant.getId())).thenReturn(List.of(testMenu));

        // Act
        List<MenuResponseDto> results = menuService.findMenusByRestaurantId(testRestaurant.getId());

        // Assert
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(testMenu.getName(), results.get(0).getName());
        verify(menuRepository).findByRestaurantId(testRestaurant.getId());
    }

    @Test
    @DisplayName("findMenusByRestaurantId - Success with No Menus")
    void findMenusByRestaurantId_whenRestaurantHasNoMenus_shouldReturnEmptyList() {
        // Arrange
        when(menuRepository.findByRestaurantId(testRestaurant.getId())).thenReturn(Collections.emptyList());

        // Act
        List<MenuResponseDto> results = menuService.findMenusByRestaurantId(testRestaurant.getId());

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(menuRepository).findByRestaurantId(testRestaurant.getId());
    }

    // --- Test cases for findActiveMenusByRestaurantId ---

    @Test
    @DisplayName("findActiveMenusByRestaurantId - Success with Active Menus")
    void findActiveMenusByRestaurantId_whenRestaurantHasActiveMenus_shouldReturnListOfMenuResponseDto() {
        // Arrange
        when(menuRepository.findByRestaurantIdAndIsActiveTrue(testRestaurant.getId())).thenReturn(List.of(testMenu));

        // Act
        List<MenuResponseDto> results = menuService.findActiveMenusByRestaurantId(testRestaurant.getId());

        // Assert
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertTrue(results.get(0).isActive());
        verify(menuRepository).findByRestaurantIdAndIsActiveTrue(testRestaurant.getId());
    }

    @Test
    @DisplayName("findActiveMenusByRestaurantId - Success with No Active Menus")
    void findActiveMenusByRestaurantId_whenRestaurantHasNoActiveMenus_shouldReturnEmptyList() {
        // Arrange
        when(menuRepository.findByRestaurantIdAndIsActiveTrue(testRestaurant.getId())).thenReturn(Collections.emptyList());

        // Act
        List<MenuResponseDto> results = menuService.findActiveMenusByRestaurantId(testRestaurant.getId());

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(menuRepository).findByRestaurantIdAndIsActiveTrue(testRestaurant.getId());
    }

    @Test
    @DisplayName("findActiveMenusByRestaurantId - Success with Mixed Active/Inactive Menus")
    void findActiveMenusByRestaurantId_whenRestaurantHasMixedMenus_shouldReturnOnlyActiveMenus() {
        // Arrange
        Menu activeMenu1 = new Menu();
        activeMenu1.setId(testMenu.getId());
        activeMenu1.setName(testMenu.getName());
        activeMenu1.setActive(true);
        activeMenu1.setRestaurant(testRestaurant);
        activeMenu1.setCreatedAt(Instant.now());
        activeMenu1.setUpdatedAt(Instant.now());

        when(menuRepository.findByRestaurantIdAndIsActiveTrue(testRestaurant.getId())).thenReturn(List.of(activeMenu1));

        // Act
        List<MenuResponseDto> results = menuService.findActiveMenusByRestaurantId(testRestaurant.getId());

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(activeMenu1.getName(), results.get(0).getName());
        assertTrue(results.get(0).isActive());
        verify(menuRepository).findByRestaurantIdAndIsActiveTrue(testRestaurant.getId());
    }


    // --- Test cases for updateMenu ---

    @Test
    @DisplayName("updateMenu - Success Full Update")
    void updateMenu_whenValidRequest_shouldReturnUpdatedMenuResponseDto() {
        // Arrange
        updateMenuRequestDto.setName("Updated Lunch Menu");
        updateMenuRequestDto.setDescription("New delicious lunch options");
        updateMenuRequestDto.setIsActive(false);

        testMenu.setName("Original Name For Full Update Test");

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), "Updated Lunch Menu"))
                .thenReturn(Optional.empty());
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu savedMenu = invocation.getArgument(0);
            savedMenu.setUpdatedAt(Instant.now().plusSeconds(1));
            return savedMenu;
        });

        // Act
        MenuResponseDto result = menuService.updateMenu(testMenu.getId(), updateMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Lunch Menu", result.getName());
        assertEquals("New delicious lunch options", result.getDescription());
        assertFalse(result.isActive());
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository).findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), "Updated Lunch Menu");
        verify(menuRepository).save(testMenu);
    }

    @Test
    @DisplayName("updateMenu - Name Conflict")
    void updateMenu_whenNewNameConflicts_shouldThrowConflictException() {
        // Arrange
        updateMenuRequestDto.setName("Conflicting Menu Name");
        Menu conflictingMenu = new Menu();
        conflictingMenu.setId(12L);
        conflictingMenu.setName("Conflicting Menu Name");
        conflictingMenu.setRestaurant(testRestaurant);

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), "Conflicting Menu Name"))
                .thenReturn(Optional.of(conflictingMenu));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> menuService.updateMenu(testMenu.getId(), updateMenuRequestDto));
        assertEquals("Another menu with name 'Conflicting Menu Name' already exists for this restaurant.", exception.getMessage());
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository).findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), "Conflicting Menu Name");
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("updateMenu - Update Only Description")
    void updateMenu_whenOnlyDescriptionProvided_shouldUpdateOnlyDescription() {
        // Arrange
        updateMenuRequestDto.setDescription("Only description updated");

        String originalName = testMenu.getName();
        boolean originalActiveStatus = testMenu.isActive();

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MenuResponseDto result = menuService.updateMenu(testMenu.getId(), updateMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(originalName, result.getName());
        assertEquals("Only description updated", result.getDescription());
        assertEquals(originalActiveStatus, result.isActive());
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository, never()).findByRestaurantIdAndNameIgnoreCase(anyLong(), anyString());
        verify(menuRepository).save(testMenu);
    }


    @Test
    @DisplayName("updateMenu - Menu Not Found")
    void updateMenu_whenMenuNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistentMenuId = 99L;
        updateMenuRequestDto.setName("Doesn't matter");
        when(menuRepository.findById(nonExistentMenuId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> menuService.updateMenu(nonExistentMenuId, updateMenuRequestDto));
        assertEquals("Menu not found with ID: " + nonExistentMenuId, exception.getMessage());
        verify(menuRepository).findById(nonExistentMenuId);
        verify(menuRepository, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("updateMenu - Update Only IsActive Status")
    void updateMenu_whenOnlyIsActiveProvided_shouldUpdateOnlyIsActive() {
        // Arrange
        updateMenuRequestDto.setIsActive(false);

        String originalName = testMenu.getName();
        String originalDescription = testMenu.getDescription();

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MenuResponseDto result = menuService.updateMenu(testMenu.getId(), updateMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(originalName, result.getName());
        assertEquals(originalDescription, result.getDescription());
        assertFalse(result.isActive());
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository, never()).findByRestaurantIdAndNameIgnoreCase(anyLong(), anyString());
        verify(menuRepository).save(testMenu);
    }

    @Test
    @DisplayName("updateMenu - Update Name to Same Name (Case Insensitive)")
    void updateMenu_whenNewNameIsSameAsOldNameIgnoreCase_shouldNotUpdateNameAndNotTriggerConflictCheck() {
        // Arrange
        String originalName = testMenu.getName();
        updateMenuRequestDto.setName(originalName.toUpperCase());

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MenuResponseDto result = menuService.updateMenu(testMenu.getId(), updateMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(originalName, result.getName());
        verify(menuRepository, never()).findByRestaurantIdAndNameIgnoreCase(
                eq(testRestaurant.getId()),
                anyString()
        );
        verify(menuRepository).save(testMenu);
    }

    @Test
    @DisplayName("updateMenu - Name Conflict Check Finds Same Menu")
    void updateMenu_whenNameConflictCheckFindsItself_shouldNotThrowConflictAndAllowUpdate() {
        // Arrange
        String newName = "Slightly New Name";
        testMenu.setName("Original Name For This Test");
        updateMenuRequestDto.setName(newName);

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), newName))
                .thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MenuResponseDto result = menuService.updateMenu(testMenu.getId(), updateMenuRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(newName, result.getName());
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository).findByRestaurantIdAndNameIgnoreCase(testRestaurant.getId(), newName);
        verify(menuRepository).save(testMenu);
    }


    // --- Test cases for deleteMenu (Soft Delete) ---

    @Test
    @DisplayName("deleteMenu - Success (Soft Delete)")
    void deleteMenu_whenMenuExistsAndIsActive_shouldSetInactiveAndSave() {
        // Arrange
        assertTrue(testMenu.isActive());
        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Mock the menuItemRepository call to return an empty list, as we are not testing item deletion here
        when(menuItemRepository.findByMenuId(testMenu.getId())).thenReturn(Collections.emptyList());

        // Act
        menuService.deleteMenu(testMenu.getId());

        // Assert
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository).save(testMenu);
        verify(menuItemRepository).findByMenuId(testMenu.getId()); // Verify this interaction
        // verify(menuItemRepository, never()).save(any(MenuItem.class)); // If no items, save on item repo not called
        assertFalse(testMenu.isActive());
    }

    @Test
    @DisplayName("deleteMenu - Menu Already Inactive")
    void deleteMenu_whenMenuExistsAndIsAlreadyInactive_shouldStillSaveAndRemainInactive() {
        // Arrange
        testMenu.setActive(false);
        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Mock the menuItemRepository call
        when(menuItemRepository.findByMenuId(testMenu.getId())).thenReturn(Collections.emptyList());


        // Act
        menuService.deleteMenu(testMenu.getId());

        // Assert
        verify(menuRepository).findById(testMenu.getId());
        verify(menuRepository).save(testMenu);
        verify(menuItemRepository).findByMenuId(testMenu.getId());
        assertFalse(testMenu.isActive());
    }

    @Test
    @DisplayName("deleteMenu - Menu Not Found")
    void deleteMenu_whenMenuNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        Long nonExistentMenuId = 99L;
        when(menuRepository.findById(nonExistentMenuId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> menuService.deleteMenu(nonExistentMenuId));
        assertEquals("Menu not found with ID: " + nonExistentMenuId, exception.getMessage());
        verify(menuRepository).findById(nonExistentMenuId);
        verify(menuRepository, never()).save(any(Menu.class));
        verify(menuItemRepository, never()).findByMenuId(anyLong()); // menuItemRepo should not be called
    }

    @Test
    @DisplayName("deleteMenu - Success with MenuItems Deactivation")
    void deleteMenu_whenMenuHasActiveItems_shouldDeactivateItemsAndMenu() {
        // Arrange
        assertTrue(testMenu.isActive());
        MenuItem item1 = new MenuItem(); item1.setId(101L); item1.setActive(true); item1.setMenu(testMenu);
        MenuItem item2 = new MenuItem(); item2.setId(102L); item2.setActive(true); item2.setMenu(testMenu);
        List<MenuItem> items = List.of(item1, item2);

        when(menuRepository.findById(testMenu.getId())).thenReturn(Optional.of(testMenu));
        when(menuItemRepository.findByMenuId(testMenu.getId())).thenReturn(items);
        when(menuRepository.save(any(Menu.class))).thenReturn(testMenu); // or thenAnswer
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));


        // Act
        menuService.deleteMenu(testMenu.getId());

        // Assert
        verify(menuRepository).findById(testMenu.getId());
        verify(menuItemRepository).findByMenuId(testMenu.getId());
        verify(menuItemRepository, times(2)).save(any(MenuItem.class)); // Called for each item
        verify(menuRepository).save(testMenu);

        assertFalse(testMenu.isActive(), "Menu should be deactivated");
        assertFalse(item1.isActive(), "Item 1 should be deactivated");
        assertFalse(item2.isActive(), "Item 2 should be deactivated");
    }
}
