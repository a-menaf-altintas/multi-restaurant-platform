package com.multirestaurantplatform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.multirestaurantplatform.common.exception.ConflictException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest // Loads the full application context
@AutoConfigureMockMvc // Configures MockMvc
@TestPropertySource(properties = {
        // Provide a dummy JWT secret for tests if your security config needs it for startup
        // This was used in AuthControllerIntegrationTest, good to keep consistent
        "app.jwt.secret=FZrx/+48fJdLdRjR7xESLZFrEbP/3gEUZhfyH9cG3mRWOGmzxZaEhyaZsSgjGCtUD2tKOuQUoqLXWosZbl9DTg=="
})
class RestaurantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // For sending HTTP requests to the controller

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to/from JSON

    @MockBean // Creates a Mockito mock for RestaurantService and injects it into the application context
    private RestaurantService restaurantService;

    private Restaurant restaurant1;
    private Restaurant restaurant2;
    private RestaurantResponseDto responseDto1;
    private CreateRestaurantRequestDto createDto;
    private UpdateRestaurantRequestDto updateDto;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        Instant now = Instant.now();
        restaurant1 = new Restaurant();
        restaurant1.setId(1L);
        restaurant1.setName("Test Restaurant One");
        restaurant1.setDescription("Description One");
        restaurant1.setAddress("123 Test St");
        restaurant1.setPhoneNumber("555-0101");
        restaurant1.setEmail("one@test.com");
        restaurant1.setActive(true); // Ensure this is set for restaurant1
        restaurant1.setCreatedAt(now);
        restaurant1.setUpdatedAt(now);

        restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        restaurant2.setName("Test Restaurant Two");
        restaurant2.setDescription("Description Two");
        restaurant2.setAddress("456 Other Ave");
        restaurant2.setPhoneNumber("555-0202");
        restaurant2.setEmail("two@test.com");
        restaurant2.setActive(true);
        restaurant2.setCreatedAt(now);
        restaurant2.setUpdatedAt(now.plusSeconds(60));

        responseDto1 = new RestaurantResponseDto(
                restaurant1.getId(), restaurant1.getName(), restaurant1.getDescription(),
                restaurant1.getAddress(), restaurant1.getPhoneNumber(), restaurant1.getEmail(),
                restaurant1.isActive(), restaurant1.getCreatedAt(), restaurant1.getUpdatedAt()
        );

        createDto = new CreateRestaurantRequestDto();
        createDto.setName("New Awesome Restaurant");
        createDto.setDescription("A truly awesome place.");
        createDto.setAddress("789 Awesome Rd");
        createDto.setPhoneNumber("555-0303");
        createDto.setEmail("awesome@test.com");

        updateDto = new UpdateRestaurantRequestDto();
    }

    // --- POST /api/v1/restaurants ---

    @Test
    @DisplayName("POST /restaurants - Success (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createRestaurant_whenAdminAndValidData_shouldReturn201AndRestaurant() throws Exception {
        Restaurant createdRestaurant = new Restaurant();
        createdRestaurant.setId(3L);
        createdRestaurant.setName(createDto.getName());
        createdRestaurant.setDescription(createDto.getDescription());
        createdRestaurant.setAddress(createDto.getAddress());
        createdRestaurant.setPhoneNumber(createDto.getPhoneNumber());
        createdRestaurant.setEmail(createDto.getEmail());
        createdRestaurant.setActive(true); // Explicitly set for the response DTO mapping
        createdRestaurant.setCreatedAt(Instant.now());
        createdRestaurant.setUpdatedAt(Instant.now());

        when(restaurantService.createRestaurant(any(CreateRestaurantRequestDto.class))).thenReturn(createdRestaurant);

        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)));

        resultActions.andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(3)))
                .andExpect(jsonPath("$.name", is(createDto.getName())))
                .andExpect(jsonPath("$.description", is(createDto.getDescription())))
                .andExpect(jsonPath("$.active", is(true))); // Check for 'active'
        verify(restaurantService).createRestaurant(any(CreateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("POST /restaurants - Validation Error (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createRestaurant_whenAdminAndInvalidData_shouldReturn400() throws Exception {
        CreateRestaurantRequestDto invalidDto = new CreateRestaurantRequestDto();
        invalidDto.setName("");

        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)));

        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors", hasItem(containsString("Restaurant name cannot be blank"))));
        verify(restaurantService, never()).createRestaurant(any());
    }

    @Test
    @DisplayName("POST /restaurants - Name Conflict (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createRestaurant_whenNameConflict_shouldReturn409() throws Exception {
        when(restaurantService.createRestaurant(any(CreateRestaurantRequestDto.class)))
                .thenThrow(new ConflictException("Restaurant with name '" + createDto.getName() + "' already exists."));

        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)));

        resultActions.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }


    @Test
    @DisplayName("POST /restaurants - Forbidden (CUSTOMER)")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void createRestaurant_whenCustomer_shouldReturn403Forbidden() throws Exception {
        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)));

        resultActions.andExpect(status().isForbidden());
        verify(restaurantService, never()).createRestaurant(any());
    }

    @Test
    @DisplayName("POST /restaurants - Unauthorized (No Auth)")
    void createRestaurant_whenNoAuth_shouldReturn401Unauthorized() throws Exception {
        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)));

        resultActions.andExpect(status().isUnauthorized());
        verify(restaurantService, never()).createRestaurant(any());
    }

    // --- GET /api/v1/restaurants/{id} ---

    @Test
    @DisplayName("GET /restaurants/{id} - Success (Authenticated User)")
    @WithMockUser(username = "anyuser")
    void getRestaurantById_whenExistsAndAuthenticated_shouldReturn200AndRestaurant() throws Exception {
        when(restaurantService.findRestaurantById(1L)).thenReturn(restaurant1);

        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants/1")
                .accept(MediaType.APPLICATION_JSON));

        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is(restaurant1.getName())))
                .andExpect(jsonPath("$.active", is(restaurant1.isActive()))); // Check for 'active'
        verify(restaurantService).findRestaurantById(1L);
    }

    @Test
    @DisplayName("GET /restaurants/{id} - Not Found (Authenticated User)")
    @WithMockUser(username = "anyuser")
    void getRestaurantById_whenNotExists_shouldReturn404() throws Exception {
        when(restaurantService.findRestaurantById(99L)).thenThrow(new ResourceNotFoundException("Restaurant not found with ID: 99"));

        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants/99")
                .accept(MediaType.APPLICATION_JSON));

        resultActions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Restaurant not found with ID: 99")));
        verify(restaurantService).findRestaurantById(99L);
    }

    @Test
    @DisplayName("GET /restaurants/{id} - Unauthorized (No Auth)")
    void getRestaurantById_whenNoAuth_shouldReturn401() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants/1")
                .accept(MediaType.APPLICATION_JSON));
        resultActions.andExpect(status().isUnauthorized());
    }


    // --- GET /api/v1/restaurants ---

    @Test
    @DisplayName("GET /restaurants - Success with Data (Authenticated User)")
    @WithMockUser(username = "anyuser")
    void getAllRestaurants_whenDataExists_shouldReturn200AndListOfRestaurants() throws Exception {
        List<Restaurant> restaurants = Arrays.asList(restaurant1, restaurant2);
        when(restaurantService.findAllRestaurants()).thenReturn(restaurants);

        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants")
                .accept(MediaType.APPLICATION_JSON));

        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is(restaurant1.getName())))
                .andExpect(jsonPath("$[0].active", is(restaurant1.isActive()))) // Check for 'active'
                .andExpect(jsonPath("$[1].name", is(restaurant2.getName())))
                .andExpect(jsonPath("$[1].active", is(restaurant2.isActive()))); // Check for 'active'
        verify(restaurantService).findAllRestaurants();
    }

    @Test
    @DisplayName("GET /restaurants - Success Empty List (Authenticated User)")
    @WithMockUser(username = "anyuser")
    void getAllRestaurants_whenNoData_shouldReturn200AndEmptyList() throws Exception {
        when(restaurantService.findAllRestaurants()).thenReturn(Collections.emptyList());

        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants")
                .accept(MediaType.APPLICATION_JSON));

        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
        verify(restaurantService).findAllRestaurants();
    }

    @Test
    @DisplayName("GET /restaurants - Unauthorized (No Auth)")
    void getAllRestaurants_whenNoAuth_shouldReturn401() throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants")
                .accept(MediaType.APPLICATION_JSON));
        resultActions.andExpect(status().isUnauthorized());
    }

    // --- PUT /api/v1/restaurants/{id} ---

    @Test
    @DisplayName("PUT /restaurants/{id} - Success (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateRestaurant_whenAdminAndValidData_shouldReturn200AndUpdatedRestaurant() throws Exception {
        Long restaurantId = 1L;
        updateDto.setDescription("Updated Description by Admin");
        updateDto.setIsActive(false);

        Restaurant updatedRestaurant = new Restaurant();
        updatedRestaurant.setId(restaurantId);
        updatedRestaurant.setName(restaurant1.getName());
        updatedRestaurant.setDescription(updateDto.getDescription());
        updatedRestaurant.setActive(updateDto.getIsActive());
        // Make sure to set all fields that RestaurantResponseDto expects,
        // otherwise they might be null and Jackson might omit them if configured to do so,
        // or it might cause issues if primitive types in DTO expect non-null.
        updatedRestaurant.setAddress(restaurant1.getAddress());
        updatedRestaurant.setPhoneNumber(restaurant1.getPhoneNumber());
        updatedRestaurant.setEmail(restaurant1.getEmail());
        updatedRestaurant.setCreatedAt(restaurant1.getCreatedAt());
        updatedRestaurant.setUpdatedAt(Instant.now());


        when(restaurantService.updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequestDto.class)))
                .thenReturn(updatedRestaurant);

        ResultActions resultActions = mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)));

        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(restaurantId.intValue())))
                .andExpect(jsonPath("$.description", is("Updated Description by Admin")))
                .andExpect(jsonPath("$.active", is(false))); // Corrected: Check for 'active'
        verify(restaurantService).updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("PUT /restaurants/{id} - Not Found (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateRestaurant_whenAdminAndNotFound_shouldReturn404() throws Exception {
        Long nonExistentId = 99L;
        updateDto.setName("Some Update");
        when(restaurantService.updateRestaurant(eq(nonExistentId), any(UpdateRestaurantRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("Restaurant not found with ID: " + nonExistentId));

        ResultActions resultActions = mockMvc.perform(put("/api/v1/restaurants/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)));

        resultActions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
        verify(restaurantService).updateRestaurant(eq(nonExistentId), any(UpdateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("PUT /restaurants/{id} - Validation Error (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateRestaurant_whenAdminAndInvalidData_shouldReturn400() throws Exception {
        Long restaurantId = 1L;
        UpdateRestaurantRequestDto invalidUpdateDto = new UpdateRestaurantRequestDto();
        invalidUpdateDto.setName("");

        ResultActions resultActions = mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidUpdateDto)));

        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors", hasItem(containsString("Restaurant name must be between 2 and 100 characters"))));
        verify(restaurantService, never()).updateRestaurant(anyLong(), any());
    }


    @Test
    @DisplayName("PUT /restaurants/{id} - Forbidden (CUSTOMER)")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void updateRestaurant_whenCustomer_shouldReturn403Forbidden() throws Exception {
        Long restaurantId = 1L;
        updateDto.setDescription("Customer Update Attempt");

        ResultActions resultActions = mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)));

        resultActions.andExpect(status().isForbidden());
        verify(restaurantService, never()).updateRestaurant(anyLong(), any());
    }

    // --- DELETE /api/v1/restaurants/{id} ---

    @Test
    @DisplayName("DELETE /restaurants/{id} - Success (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deleteRestaurant_whenAdminAndExists_shouldReturn204NoContent() throws Exception {
        Long restaurantId = 1L;
        doNothing().when(restaurantService).deleteRestaurant(restaurantId);

        ResultActions resultActions = mockMvc.perform(delete("/api/v1/restaurants/{id}", restaurantId));

        resultActions.andExpect(status().isNoContent());
        verify(restaurantService).deleteRestaurant(restaurantId);
    }

    @Test
    @DisplayName("DELETE /restaurants/{id} - Not Found (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deleteRestaurant_whenAdminAndNotFound_shouldReturn404() throws Exception {
        Long nonExistentId = 99L;
        doThrow(new ResourceNotFoundException("Restaurant not found with ID: " + nonExistentId))
                .when(restaurantService).deleteRestaurant(nonExistentId);

        ResultActions resultActions = mockMvc.perform(delete("/api/v1/restaurants/{id}", nonExistentId));

        resultActions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
        verify(restaurantService).deleteRestaurant(nonExistentId);
    }

    @Test
    @DisplayName("DELETE /restaurants/{id} - Forbidden (CUSTOMER)")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void deleteRestaurant_whenCustomer_shouldReturn403Forbidden() throws Exception {
        Long restaurantId = 1L;

        ResultActions resultActions = mockMvc.perform(delete("/api/v1/restaurants/{id}", restaurantId));

        resultActions.andExpect(status().isForbidden());
        verify(restaurantService, never()).deleteRestaurant(anyLong());
    }
}
