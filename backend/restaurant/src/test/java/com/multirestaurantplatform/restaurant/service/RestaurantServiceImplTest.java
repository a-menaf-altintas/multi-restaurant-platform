// File: backend/restaurant/src/test/java/com/multirestaurantplatform/restaurant/service/RestaurantServiceImplTest.java
package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.common.exception.BadRequestException;
import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.model.Role; // Ensure this import is correct
import com.multirestaurantplatform.security.model.User;   // Ensure this import is correct
import com.multirestaurantplatform.security.repository.UserRepository; // Import UserRepository

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository; // Added UserRepository mock

    @InjectMocks
    private RestaurantServiceImpl restaurantService;

    private Restaurant restaurant;
    private CreateRestaurantRequestDto createDto;
    private UpdateRestaurantRequestDto updateDto;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setAddress("123 Test St");
        restaurant.setPhoneNumber("555-1234");
        restaurant.setActive(true);
        restaurant.setRestaurantAdmins(new HashSet<>()); // Initialize admin set

        createDto = new CreateRestaurantRequestDto();
        createDto.setName("New Restaurant");
        createDto.setEmail("new@restaurant.com");
        createDto.setAddress("456 New Ave");
        createDto.setPhoneNumber("555-5678");
        createDto.setDescription("A new place to eat.");

        updateDto = new UpdateRestaurantRequestDto();

        sampleUser = new User(); // Basic user for tests, customize as needed in User model
        sampleUser.setId(100L);
        sampleUser.setUsername("testuser");
        // sampleUser.setRoles(new HashSet<>()); // Assuming User has setRoles
    }

    // --- Test cases for createRestaurant ---

    @Test
    @DisplayName("createRestaurant - Success")
    void createRestaurant_whenValidRequest_shouldReturnCreatedRestaurant() {
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.findByEmail(createDto.getEmail())).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(2L);
            return r;
        });

        Restaurant createdRestaurant = restaurantService.createRestaurant(createDto);

        assertNotNull(createdRestaurant);
        assertEquals(createDto.getName(), createdRestaurant.getName());
        assertEquals(createDto.getEmail(), createdRestaurant.getEmail());
        assertEquals(2L, createdRestaurant.getId());
        assertTrue(createdRestaurant.isActive());
        assertTrue(createdRestaurant.getRestaurantAdmins().isEmpty()); // Ensure admins are initially empty
        verify(restaurantRepository).findByName(createDto.getName());
        verify(restaurantRepository).findByEmail(createDto.getEmail());
        verify(restaurantRepository).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Name Conflict")
    void createRestaurant_whenNameExists_shouldThrowConflictException() {
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.of(restaurant));

        ConflictException exception = assertThrows(ConflictException.class, () -> restaurantService.createRestaurant(createDto));
        assertEquals("Restaurant with name '" + createDto.getName() + "' already exists.", exception.getMessage());
        verify(restaurantRepository).findByName(createDto.getName());
        verify(restaurantRepository, never()).findByEmail(anyString());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Email Conflict")
    void createRestaurant_whenEmailExists_shouldThrowConflictException() {
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.findByEmail(createDto.getEmail())).thenReturn(Optional.of(restaurant));

        ConflictException exception = assertThrows(ConflictException.class, () -> restaurantService.createRestaurant(createDto));
        assertEquals("Restaurant with email '" + createDto.getEmail() + "' already exists.", exception.getMessage());
        verify(restaurantRepository).findByName(createDto.getName());
        verify(restaurantRepository).findByEmail(createDto.getEmail());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Success with Null Email")
    void createRestaurant_whenEmailIsNull_shouldCreateSuccessfully() {
        createDto.setEmail(null);
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(3L);
            return r;
        });

        Restaurant createdRestaurant = restaurantService.createRestaurant(createDto);

        assertNotNull(createdRestaurant);
        assertEquals(createDto.getName(), createdRestaurant.getName());
        assertNull(createdRestaurant.getEmail());
        verify(restaurantRepository).findByName(createDto.getName());
        verify(restaurantRepository, never()).findByEmail(anyString());
        verify(restaurantRepository).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Success with Empty Email")
    void createRestaurant_whenEmailIsEmpty_shouldCreateSuccessfully() {
        createDto.setEmail("");
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(4L);
            return r;
        });

        Restaurant createdRestaurant = restaurantService.createRestaurant(createDto);

        assertNotNull(createdRestaurant);
        assertEquals(createDto.getName(), createdRestaurant.getName());
        assertEquals("", createdRestaurant.getEmail()); // Or assertNull if business logic changes empty string to null
        verify(restaurantRepository).findByName(createDto.getName());
        verify(restaurantRepository, never()).findByEmail(anyString()); // Service checks StringUtils.hasText()
        verify(restaurantRepository).save(any(Restaurant.class));
    }


    // --- Test cases for findRestaurantById ---

    @Test
    @DisplayName("findRestaurantById - Success")
    void findRestaurantById_whenRestaurantExists_shouldReturnRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        Restaurant foundRestaurant = restaurantService.findRestaurantById(1L);
        assertNotNull(foundRestaurant);
        assertEquals(restaurant.getId(), foundRestaurant.getId());
        verify(restaurantRepository).findById(1L);
    }

    @Test
    @DisplayName("findRestaurantById - Not Found")
    void findRestaurantById_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> restaurantService.findRestaurantById(1L));
        assertEquals("Restaurant not found with ID: 1", exception.getMessage());
        verify(restaurantRepository).findById(1L);
    }

    // --- Test cases for findAllRestaurants ---

    @Test
    @DisplayName("findAllRestaurants - Success with Restaurants")
    void findAllRestaurants_whenRestaurantsExist_shouldReturnListOfRestaurants() {
        when(restaurantRepository.findAll()).thenReturn(List.of(restaurant));
        List<Restaurant> restaurants = restaurantService.findAllRestaurants();
        assertNotNull(restaurants);
        assertFalse(restaurants.isEmpty());
        assertEquals(1, restaurants.size());
        verify(restaurantRepository).findAll();
    }

    @Test
    @DisplayName("findAllRestaurants - Success with No Restaurants")
    void findAllRestaurants_whenNoRestaurantsExist_shouldReturnEmptyList() {
        when(restaurantRepository.findAll()).thenReturn(Collections.emptyList());
        List<Restaurant> restaurants = restaurantService.findAllRestaurants();
        assertNotNull(restaurants);
        assertTrue(restaurants.isEmpty());
        verify(restaurantRepository).findAll();
    }

    // --- Test cases for updateRestaurant ---

    @Test
    @DisplayName("updateRestaurant - Success Full Update (No Admins)")
    void updateRestaurant_whenValidRequestNoAdmins_shouldReturnUpdatedRestaurant() {
        Long restaurantId = 1L;
        updateDto.setName("Updated Name");
        updateDto.setEmail("updated@restaurant.com");
        updateDto.setDescription("Updated Description");
        updateDto.setAddress("Updated Address");
        updateDto.setPhoneNumber("555-0000");
        updateDto.setIsActive(false);
        // adminUserIds is null by default in updateDto

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName("Updated Name")).thenReturn(Optional.empty());
        when(restaurantRepository.findByEmail("updated@restaurant.com")).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertEquals("Updated Name", updatedRestaurant.getName());
        assertEquals("updated@restaurant.com", updatedRestaurant.getEmail());
        assertEquals("Updated Description", updatedRestaurant.getDescription());
        assertEquals("Updated Address", updatedRestaurant.getAddress());
        assertEquals("555-0000", updatedRestaurant.getPhoneNumber());
        assertFalse(updatedRestaurant.isActive());
        assertTrue(updatedRestaurant.getRestaurantAdmins().isEmpty()); // Admins should remain untouched and empty
        verify(restaurantRepository).findById(restaurantId);
        verify(userRepository, never()).findById(anyLong()); // No admin interaction
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Success Partial Update (Only Name)")
    void updateRestaurant_whenPartialRequest_shouldUpdateOnlyProvidedFields() {
        Long restaurantId = 1L;
        String originalEmail = restaurant.getEmail();
        updateDto.setName("Partially Updated Name");

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName("Partially Updated Name")).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertEquals("Partially Updated Name", updatedRestaurant.getName());
        assertEquals(originalEmail, updatedRestaurant.getEmail()); // Email should not change
        assertEquals(restaurant.getDescription(), updatedRestaurant.getDescription());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Not Found")
    void updateRestaurant_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        Long restaurantId = 99L;
        updateDto.setName("Doesn't Matter");
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant not found with ID: " + restaurantId, exception.getMessage());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Name Conflict")
    void updateRestaurant_whenNewNameConflicts_shouldThrowConflictException() {
        Long restaurantId = 1L;
        String conflictingName = "Existing Other Restaurant";
        updateDto.setName(conflictingName);

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName(conflictingName);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName(conflictingName)).thenReturn(Optional.of(otherRestaurant));

        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant with name '" + conflictingName + "' already exists.", exception.getMessage());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).findByName(conflictingName);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Email Conflict")
    void updateRestaurant_whenNewEmailConflicts_shouldThrowConflictException() {
        Long restaurantId = 1L;
        String conflictingEmail = "existing.other@restaurant.com";
        updateDto.setEmail(conflictingEmail);

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setEmail(conflictingEmail);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByEmail(conflictingEmail)).thenReturn(Optional.of(otherRestaurant));

        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant with email '" + conflictingEmail + "' already exists.", exception.getMessage());
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).findByEmail(conflictingEmail);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Update Name to Same Name (No Conflict Check)")
    void updateRestaurant_whenNameIsSame_shouldNotTriggerConflictCheckForName() {
        Long restaurantId = 1L;
        updateDto.setName(restaurant.getName()); // Same name as existing

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        restaurantService.updateRestaurant(restaurantId, updateDto);

        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository, never()).findByName(restaurant.getName()); // Correct: service skips if name hasn't changed
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Update Email to Same Email (No Conflict Check)")
    void updateRestaurant_whenEmailIsSame_shouldNotTriggerConflictCheckForEmail() {
        Long restaurantId = 1L;
        updateDto.setEmail(restaurant.getEmail()); // Same email

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        restaurantService.updateRestaurant(restaurantId, updateDto);

        verify(restaurantRepository).findById(restaurantId);
        // Service logic: if (StringUtils.hasText(newEmail) && !newEmail.equals(restaurant.getEmail()))
        // Since newEmail.equals(restaurant.getEmail()) is true, the findByEmail for conflict is skipped.
        verify(restaurantRepository, never()).findByEmail(restaurant.getEmail());
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Set Email to Null with Empty String")
    void updateRestaurant_whenEmailDtoIsEmptyString_shouldSetEmailToNull() {
        Long restaurantId = 1L;
        restaurant.setEmail("original@example.com"); // Has an email initially
        updateDto.setEmail(""); // DTO requests empty string for email

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertNull(updatedRestaurant.getEmail()); // Service logic should set it to null
        verify(restaurantRepository).save(restaurant);
    }


    // --- Tests for updateRestaurant with AdminUserIds ---
    @Test
    @DisplayName("updateRestaurant - Success with Admin Assignment")
    void updateRestaurant_whenAssigningValidAdmins_shouldUpdateAdmins() {
        Long restaurantId = 1L;
        Long adminUserId1 = 10L;
        Long adminUserId2 = 11L;
        updateDto.setAdminUserIds(Set.of(adminUserId1, adminUserId2));

        User adminUser1 = new User(); adminUser1.setId(adminUserId1); adminUser1.setUsername("admin1");
        adminUser1.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        User adminUser2 = new User(); adminUser2.setId(adminUserId2); adminUser2.setUsername("admin2");
        adminUser2.setRoles(Set.of(Role.RESTAURANT_ADMIN));

        // Restaurant initially has no admins
        restaurant.setRestaurantAdmins(new HashSet<>());

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(adminUserId1)).thenReturn(Optional.of(adminUser1));
        when(userRepository.findById(adminUserId2)).thenReturn(Optional.of(adminUser2));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertEquals(2, updatedRestaurant.getRestaurantAdmins().size());
        assertTrue(updatedRestaurant.getRestaurantAdmins().contains(adminUser1));
        assertTrue(updatedRestaurant.getRestaurantAdmins().contains(adminUser2));
        verify(userRepository).findById(adminUserId1);
        verify(userRepository).findById(adminUserId2);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Admin Assignment with NonExistentUser")
    void updateRestaurant_whenAssigningNonExistentAdmin_shouldThrowResourceNotFoundException() {
        Long restaurantId = 1L;
        Long nonExistentUserId = 99L;
        updateDto.setAdminUserIds(Set.of(nonExistentUserId));

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("User not found with ID: " + nonExistentUserId + " for admin assignment.", exception.getMessage());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Admin Assignment with UserLackingRole")
    void updateRestaurant_whenAssigningUserWithoutAdminRole_shouldThrowBadRequestException() {
        Long restaurantId = 1L;
        Long userIdWithoutRole = 12L;
        updateDto.setAdminUserIds(Set.of(userIdWithoutRole));

        User userWithoutRole = new User();
        userWithoutRole.setId(userIdWithoutRole);
        userWithoutRole.setUsername("normalUser");
        userWithoutRole.setRoles(Set.of(Role.CUSTOMER)); // Not a RESTAURANT_ADMIN

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(userIdWithoutRole)).thenReturn(Optional.of(userWithoutRole));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertTrue(exception.getMessage().contains("User normalUser (ID: " + userIdWithoutRole + ") is not a RESTAURANT_ADMIN"));
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Clear Admins with Empty List")
    void updateRestaurant_whenAdminUserIdsIsEmpty_shouldClearAdmins() {
        Long restaurantId = 1L;
        User existingAdmin = new User(); existingAdmin.setId(10L); /* setup user */
        existingAdmin.setRoles(Set.of(Role.RESTAURANT_ADMIN));
        restaurant.setRestaurantAdmins(new HashSet<>(Set.of(existingAdmin))); // Has one admin

        updateDto.setAdminUserIds(Collections.emptySet()); // Request to clear admins

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertTrue(updatedRestaurant.getRestaurantAdmins().isEmpty());
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Null AdminUserIds Does Not Modify Admins")
    void updateRestaurant_whenAdminUserIdsIsNull_shouldNotModifyAdmins() {
        Long restaurantId = 1L;
        updateDto.setAdminUserIds(null); // Explicitly null, or just not set
        updateDto.setName("Only Name Change"); // Some other change to trigger save

        User existingAdmin = new User(); existingAdmin.setId(20L);  /* setup user */
        existingAdmin.setRoles(Set.of(Role.RESTAURANT_ADMIN));
        restaurant.setRestaurantAdmins(new HashSet<>(Set.of(existingAdmin))); // Restaurant starts with an admin

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName("Only Name Change")).thenReturn(Optional.empty()); // For name update
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        assertNotNull(updatedRestaurant);
        assertEquals("Only Name Change", updatedRestaurant.getName());
        assertEquals(1, updatedRestaurant.getRestaurantAdmins().size()); // Admins should be untouched
        assertTrue(updatedRestaurant.getRestaurantAdmins().contains(existingAdmin));
        verify(userRepository, never()).findById(anyLong()); // No interaction for admin assignment
        verify(restaurantRepository).save(restaurant);
    }


    // --- Test cases for deleteRestaurant ---

    @Test
    @DisplayName("deleteRestaurant - Success")
    void deleteRestaurant_whenRestaurantExists_shouldDeleteRestaurant() {
        Long restaurantId = 1L;
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        doNothing().when(restaurantRepository).deleteById(restaurantId);

        restaurantService.deleteRestaurant(restaurantId);

        verify(restaurantRepository).existsById(restaurantId);
        verify(restaurantRepository).deleteById(restaurantId);
    }

    @Test
    @DisplayName("deleteRestaurant - Not Found")
    void deleteRestaurant_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        Long restaurantId = 99L;
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.deleteRestaurant(restaurantId);
        });
        assertEquals("Restaurant not found with ID: " + restaurantId + " for deletion.", exception.getMessage());
        verify(restaurantRepository).existsById(restaurantId);
        verify(restaurantRepository, never()).deleteById(anyLong());
    }
}