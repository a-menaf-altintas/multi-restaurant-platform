package com.multirestaurantplatform.restaurant.service;

import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantServiceImpl restaurantService;

    private Restaurant restaurant;
    private CreateRestaurantRequestDto createDto;
    private UpdateRestaurantRequestDto updateDto;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setAddress("123 Test St");
        restaurant.setPhoneNumber("555-1234");
        restaurant.setActive(true);

        createDto = new CreateRestaurantRequestDto();
        createDto.setName("New Restaurant");
        createDto.setEmail("new@restaurant.com");
        createDto.setAddress("456 New Ave");
        createDto.setPhoneNumber("555-5678");
        createDto.setDescription("A new place to eat.");

        // Initialize updateDto, but set fields in specific tests
        updateDto = new UpdateRestaurantRequestDto();
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
    @DisplayName("updateRestaurant - Success Full Update")
    void updateRestaurant_whenValidRequest_shouldReturnUpdatedRestaurant() {
        Long restaurantId = 1L;
        // Set fields directly, not with Optional.of()
        updateDto.setName("Updated Name");
        updateDto.setEmail("updated@restaurant.com");
        updateDto.setDescription("Updated Description");
        updateDto.setAddress("Updated Address");
        updateDto.setPhoneNumber("555-0000");
        updateDto.setIsActive(false);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        // Mock conflict checks for new name/email if they are different from original
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
        verify(restaurantRepository).findById(restaurantId);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Success Partial Update (Only Name)")
    void updateRestaurant_whenPartialRequest_shouldUpdateOnlyProvidedFields() {
        Long restaurantId = 1L;
        String originalEmail = restaurant.getEmail();
        // Set only the name directly
        updateDto.setName("Partially Updated Name");
        // Other fields in updateDto remain null

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
        updateDto.setName("Doesn't Matter"); // Set directly
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
        updateDto.setName(conflictingName); // Set directly

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
        updateDto.setEmail(conflictingEmail); // Set directly
        // updateDto.setName(null); // Ensure name is not being updated or doesn't conflict

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setEmail(conflictingEmail);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        // If name is also being updated, mock its non-conflict:
        // if (updateDto.getName() != null) {
        //    when(restaurantRepository.findByName(updateDto.getName())).thenReturn(Optional.empty());
        // }
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
        updateDto.setName(restaurant.getName()); // Set directly - same name as existing

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        restaurantService.updateRestaurant(restaurantId, updateDto);

        verify(restaurantRepository).findById(restaurantId);
        // Since the name in DTO is the same as the existing one, the findByName check for conflict should be skipped.
        verify(restaurantRepository, never()).findByName(restaurant.getName());
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
