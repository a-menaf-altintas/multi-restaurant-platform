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

@ExtendWith(MockitoExtension.class) // Integrates Mockito with JUnit 5
class RestaurantServiceImplTest {

    @Mock // Creates a mock instance of RestaurantRepository
    private RestaurantRepository restaurantRepository;

    @InjectMocks // Creates an instance of RestaurantServiceImpl and injects the mock(s) into it
    private RestaurantServiceImpl restaurantService;

    private Restaurant restaurant;
    private CreateRestaurantRequestDto createDto;
    private UpdateRestaurantRequestDto updateDto;

    @BeforeEach
    void setUp() {
        // Initialize common test objects
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

        updateDto = new UpdateRestaurantRequestDto();
        // Update DTO fields will be set in specific tests
    }

    // --- Test cases for createRestaurant ---

    @Test
    @DisplayName("createRestaurant - Success")
    void createRestaurant_whenValidRequest_shouldReturnCreatedRestaurant() {
        // Arrange
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.findByEmail(createDto.getEmail())).thenReturn(Optional.empty());
        // Mock the save operation to return a restaurant with an ID
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(2L); // Simulate ID generation
            return r;
        });

        // Act
        Restaurant createdRestaurant = restaurantService.createRestaurant(createDto);

        // Assert
        assertNotNull(createdRestaurant);
        assertEquals(createDto.getName(), createdRestaurant.getName());
        assertEquals(createDto.getEmail(), createdRestaurant.getEmail());
        assertEquals(2L, createdRestaurant.getId()); // Check if ID was set
        assertTrue(createdRestaurant.isActive());
        verify(restaurantRepository, times(1)).findByName(createDto.getName());
        verify(restaurantRepository, times(1)).findByEmail(createDto.getEmail());
        verify(restaurantRepository, times(1)).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Name Conflict")
    void createRestaurant_whenNameExists_shouldThrowConflictException() {
        // Arrange
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.of(restaurant)); // Existing restaurant with same name

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.createRestaurant(createDto);
        });
        assertEquals("Restaurant with name '" + createDto.getName() + "' already exists.", exception.getMessage());
        verify(restaurantRepository, times(1)).findByName(createDto.getName());
        verify(restaurantRepository, never()).findByEmail(anyString());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("createRestaurant - Email Conflict")
    void createRestaurant_whenEmailExists_shouldThrowConflictException() {
        // Arrange
        when(restaurantRepository.findByName(createDto.getName())).thenReturn(Optional.empty());
        when(restaurantRepository.findByEmail(createDto.getEmail())).thenReturn(Optional.of(restaurant)); // Existing restaurant with same email

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.createRestaurant(createDto);
        });
        assertEquals("Restaurant with email '" + createDto.getEmail() + "' already exists.", exception.getMessage());
        verify(restaurantRepository, times(1)).findByName(createDto.getName());
        verify(restaurantRepository, times(1)).findByEmail(createDto.getEmail());
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    // --- Test cases for findRestaurantById ---

    @Test
    @DisplayName("findRestaurantById - Success")
    void findRestaurantById_whenRestaurantExists_shouldReturnRestaurant() {
        // Arrange
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        // Act
        Restaurant foundRestaurant = restaurantService.findRestaurantById(1L);

        // Assert
        assertNotNull(foundRestaurant);
        assertEquals(restaurant.getId(), foundRestaurant.getId());
        assertEquals(restaurant.getName(), foundRestaurant.getName());
        verify(restaurantRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("findRestaurantById - Not Found")
    void findRestaurantById_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.findRestaurantById(1L);
        });
        assertEquals("Restaurant not found with ID: 1", exception.getMessage());
        verify(restaurantRepository, times(1)).findById(1L);
    }

    // --- Test cases for findAllRestaurants ---

    @Test
    @DisplayName("findAllRestaurants - Success with Restaurants")
    void findAllRestaurants_whenRestaurantsExist_shouldReturnListOfRestaurants() {
        // Arrange
        when(restaurantRepository.findAll()).thenReturn(List.of(restaurant));

        // Act
        List<Restaurant> restaurants = restaurantService.findAllRestaurants();

        // Assert
        assertNotNull(restaurants);
        assertFalse(restaurants.isEmpty());
        assertEquals(1, restaurants.size());
        assertEquals(restaurant.getName(), restaurants.get(0).getName());
        verify(restaurantRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAllRestaurants - Success with No Restaurants")
    void findAllRestaurants_whenNoRestaurantsExist_shouldReturnEmptyList() {
        // Arrange
        when(restaurantRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<Restaurant> restaurants = restaurantService.findAllRestaurants();

        // Assert
        assertNotNull(restaurants);
        assertTrue(restaurants.isEmpty());
        verify(restaurantRepository, times(1)).findAll();
    }

    // --- Test cases for updateRestaurant ---

    @Test
    @DisplayName("updateRestaurant - Success Full Update")
    void updateRestaurant_whenValidRequest_shouldReturnUpdatedRestaurant() {
        // Arrange
        Long restaurantId = 1L;
        updateDto.setName(Optional.of("Updated Name"));
        updateDto.setEmail(Optional.of("updated@restaurant.com"));
        updateDto.setDescription(Optional.of("Updated Description"));
        updateDto.setAddress(Optional.of("Updated Address"));
        updateDto.setPhoneNumber(Optional.of("555-0000"));
        updateDto.setIsActive(Optional.of(false));

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName("Updated Name")).thenReturn(Optional.empty()); // No conflict for new name
        when(restaurantRepository.findByEmail("updated@restaurant.com")).thenReturn(Optional.empty()); // No conflict for new email
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        // Assert
        assertNotNull(updatedRestaurant);
        assertEquals("Updated Name", updatedRestaurant.getName());
        assertEquals("updated@restaurant.com", updatedRestaurant.getEmail());
        assertEquals("Updated Description", updatedRestaurant.getDescription());
        assertEquals("Updated Address", updatedRestaurant.getAddress());
        assertEquals("555-0000", updatedRestaurant.getPhoneNumber());
        assertFalse(updatedRestaurant.isActive());
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, times(1)).save(restaurant); // or any(Restaurant.class)
    }
    
    @Test
    @DisplayName("updateRestaurant - Success Partial Update (Only Name)")
    void updateRestaurant_whenPartialRequest_shouldUpdateOnlyProvidedFields() {
        // Arrange
        Long restaurantId = 1L;
        String originalEmail = restaurant.getEmail(); // Keep original email
        updateDto.setName(Optional.of("Partially Updated Name"));
        // Other fields in updateDto remain Optional.empty()

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName("Partially Updated Name")).thenReturn(Optional.empty());
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurantId, updateDto);

        // Assert
        assertNotNull(updatedRestaurant);
        assertEquals("Partially Updated Name", updatedRestaurant.getName());
        assertEquals(originalEmail, updatedRestaurant.getEmail()); // Email should not change
        assertEquals(restaurant.getDescription(), updatedRestaurant.getDescription()); // Description should not change
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, times(1)).save(restaurant);
    }

    @Test
    @DisplayName("updateRestaurant - Not Found")
    void updateRestaurant_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        Long restaurantId = 99L;
        updateDto.setName(Optional.of("Doesn't Matter"));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant not found with ID: " + restaurantId, exception.getMessage());
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Name Conflict")
    void updateRestaurant_whenNewNameConflicts_shouldThrowConflictException() {
        // Arrange
        Long restaurantId = 1L;
        String conflictingName = "Existing Other Restaurant";
        updateDto.setName(Optional.of(conflictingName));

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L); // Different ID
        otherRestaurant.setName(conflictingName);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.findByName(conflictingName)).thenReturn(Optional.of(otherRestaurant));

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant with name '" + conflictingName + "' already exists.", exception.getMessage());
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, times(1)).findByName(conflictingName);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("updateRestaurant - Email Conflict")
    void updateRestaurant_whenNewEmailConflicts_shouldThrowConflictException() {
        // Arrange
        Long restaurantId = 1L;
        String conflictingEmail = "existing.other@restaurant.com";
        updateDto.setEmail(Optional.of(conflictingEmail));

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L); // Different ID
        otherRestaurant.setEmail(conflictingEmail);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        // Assume name update doesn't conflict or isn't provided
        updateDto.getName().ifPresent(name -> when(restaurantRepository.findByName(name)).thenReturn(Optional.empty()));
        when(restaurantRepository.findByEmail(conflictingEmail)).thenReturn(Optional.of(otherRestaurant));


        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class, () -> {
            restaurantService.updateRestaurant(restaurantId, updateDto);
        });
        assertEquals("Restaurant with email '" + conflictingEmail + "' already exists.", exception.getMessage());
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, times(1)).findByEmail(conflictingEmail);
        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }
    
    @Test
    @DisplayName("updateRestaurant - Update Name to Same Name (No Conflict Check)")
    void updateRestaurant_whenNameIsSame_shouldNotTriggerConflictCheckForName() {
        // Arrange
        Long restaurantId = 1L;
        updateDto.setName(Optional.of(restaurant.getName())); // Same name as existing

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        // Act
        restaurantService.updateRestaurant(restaurantId, updateDto);

        // Assert
        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, never()).findByName(restaurant.getName()); // Should not be called if name hasn't changed effectively
        verify(restaurantRepository, times(1)).save(restaurant);
    }


    // --- Test cases for deleteRestaurant ---

    @Test
    @DisplayName("deleteRestaurant - Success")
    void deleteRestaurant_whenRestaurantExists_shouldDeleteRestaurant() {
        // Arrange
        Long restaurantId = 1L;
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        doNothing().when(restaurantRepository).deleteById(restaurantId); // For void methods

        // Act
        restaurantService.deleteRestaurant(restaurantId);

        // Assert
        verify(restaurantRepository, times(1)).existsById(restaurantId);
        verify(restaurantRepository, times(1)).deleteById(restaurantId);
    }

    @Test
    @DisplayName("deleteRestaurant - Not Found")
    void deleteRestaurant_whenRestaurantDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        Long restaurantId = 99L;
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            restaurantService.deleteRestaurant(restaurantId);
        });
        assertEquals("Restaurant not found with ID: " + restaurantId + " for deletion.", exception.getMessage());
        verify(restaurantRepository, times(1)).existsById(restaurantId);
        verify(restaurantRepository, never()).deleteById(anyLong());
    }
}
