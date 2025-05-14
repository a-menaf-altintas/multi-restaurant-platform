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
import com.multirestaurantplatform.restaurant.service.RestaurantSecurityServiceImpl; // Import this
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
        "app.jwt.secret=FZrx/+48fJdLdRjR7xESLZFrEbP/3gEUZhfyH9cG3mRWOGmzxZaEhyaZsSgjGCtUD2tKOuQUoqLXWosZbl9DTg=="
})
class RestaurantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // For sending HTTP requests to the controller

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to/from JSON

    @MockBean // Creates a Mockito mock for RestaurantService and injects it into the application context
    private RestaurantService restaurantService;

    @MockBean // Mock the RestaurantSecurityServiceImpl for testing SpEL in @PreAuthorize
    private RestaurantSecurityServiceImpl restaurantSecurityServiceImpl;

    private Restaurant restaurant1;
    private Restaurant restaurant2;
    // private RestaurantResponseDto responseDto1; // Not directly used in current tests after setUp
    private CreateRestaurantRequestDto createDto;
    private UpdateRestaurantRequestDto updateDto;

    @BeforeEach
    void setUp() {
        // It's good practice to register JavaTimeModule if not already globally configured
        // especially if any DTOs or entities used directly in mock returns have Instant/LocalDateTime etc.
        if (objectMapper.getRegisteredModuleIds().stream().noneMatch(id -> id.toString().contains("JavaTimeModule"))) {
            objectMapper.registerModule(new JavaTimeModule());
        }


        Instant now = Instant.now();
        restaurant1 = new Restaurant();
        restaurant1.setId(1L);
        restaurant1.setName("Test Restaurant One");
        restaurant1.setDescription("Description One");
        restaurant1.setAddress("123 Test St");
        restaurant1.setPhoneNumber("555-0101");
        restaurant1.setEmail("one@test.com");
        restaurant1.setActive(true);
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

        // responseDto1 is set up but not explicitly used later in provided tests, can be removed if truly unused
        /*
        responseDto1 = new RestaurantResponseDto(
                restaurant1.getId(), restaurant1.getName(), restaurant1.getDescription(),
                restaurant1.getAddress(), restaurant1.getPhoneNumber(), restaurant1.getEmail(),
                restaurant1.isActive(), restaurant1.getCreatedAt(), restaurant1.getUpdatedAt()
        );
        */

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
        createdRestaurant.setActive(true);
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
                .andExpect(jsonPath("$.active", is(true)));
        verify(restaurantService).createRestaurant(any(CreateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("POST /restaurants - Validation Error (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createRestaurant_whenAdminAndInvalidData_shouldReturn400() throws Exception {
        CreateRestaurantRequestDto invalidDto = new CreateRestaurantRequestDto();
        invalidDto.setName(""); // Assuming name is @NotBlank or @NotEmpty

        ResultActions resultActions = mockMvc.perform(post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)));

        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                // Adjust this based on your actual validation message and ErrorResponse structure
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
    @WithMockUser(username = "anyuser") // Any authenticated user
    void getRestaurantById_whenExistsAndAuthenticated_shouldReturn200AndRestaurant() throws Exception {
        when(restaurantService.findRestaurantById(1L)).thenReturn(restaurant1);

        ResultActions resultActions = mockMvc.perform(get("/api/v1/restaurants/1")
                .accept(MediaType.APPLICATION_JSON));

        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is(restaurant1.getName())))
                .andExpect(jsonPath("$.description", is(restaurant1.getDescription())))
                .andExpect(jsonPath("$.address", is(restaurant1.getAddress())))
                .andExpect(jsonPath("$.phoneNumber", is(restaurant1.getPhoneNumber())))
                .andExpect(jsonPath("$.email", is(restaurant1.getEmail())))
                .andExpect(jsonPath("$.active", is(restaurant1.isActive())))
                .andExpect(jsonPath("$.createdAt", is(restaurant1.getCreatedAt().toString())))
                .andExpect(jsonPath("$.updatedAt", is(restaurant1.getUpdatedAt().toString())));
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
                .andExpect(jsonPath("$[0].active", is(restaurant1.isActive())))
                .andExpect(jsonPath("$[1].name", is(restaurant2.getName())))
                .andExpect(jsonPath("$[1].active", is(restaurant2.isActive())));
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
        updatedRestaurant.setName(restaurant1.getName()); // Assuming name not changed in this DTO
        updatedRestaurant.setDescription(updateDto.getDescription());
        updatedRestaurant.setActive(updateDto.getIsActive());
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
                .andExpect(jsonPath("$.active", is(false)));
        verify(restaurantService).updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("PUT /restaurants/{id} - Success (RESTAURANT_ADMIN authorized for this restaurant)")
    @WithMockUser(username = "resto_owner_1", roles = {"RESTAURANT_ADMIN"})
    void updateRestaurant_whenRestaurantAdminAndAuthorizedForRestaurant_shouldReturn200AndUpdatedRestaurant() throws Exception {
        Long restaurantId = 1L;
        String updaterUsername = "resto_owner_1";

        updateDto.setName("Restaurant Updated by Owner");
        updateDto.setDescription("Owner made some vital updates.");
        updateDto.setIsActive(true);

        Restaurant updatedRestaurantFromService = new Restaurant();
        updatedRestaurantFromService.setId(restaurantId);
        updatedRestaurantFromService.setName(updateDto.getName());
        updatedRestaurantFromService.setDescription(updateDto.getDescription());
        updatedRestaurantFromService.setActive(updateDto.getIsActive());
        updatedRestaurantFromService.setAddress(restaurant1.getAddress());
        updatedRestaurantFromService.setPhoneNumber(restaurant1.getPhoneNumber());
        updatedRestaurantFromService.setEmail(restaurant1.getEmail());
        updatedRestaurantFromService.setCreatedAt(restaurant1.getCreatedAt());
        updatedRestaurantFromService.setUpdatedAt(Instant.now());

        when(restaurantSecurityServiceImpl.isRestaurantAdminForRestaurant(eq(restaurantId), eq(updaterUsername)))
                .thenReturn(true);
        when(restaurantService.updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequestDto.class)))
                .thenReturn(updatedRestaurantFromService);

        mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(restaurantId.intValue())))
                .andExpect(jsonPath("$.name", is("Restaurant Updated by Owner")))
                .andExpect(jsonPath("$.description", is("Owner made some vital updates.")))
                .andExpect(jsonPath("$.active", is(true)));

        verify(restaurantSecurityServiceImpl).isRestaurantAdminForRestaurant(eq(restaurantId), eq(updaterUsername));
        verify(restaurantService).updateRestaurant(eq(restaurantId), any(UpdateRestaurantRequestDto.class));
    }

    @Test
    @DisplayName("PUT /restaurants/{id} - Forbidden (RESTAURANT_ADMIN not authorized for this restaurant)")
    @WithMockUser(username = "another_resto_admin", roles = {"RESTAURANT_ADMIN"})
    void updateRestaurant_whenRestaurantAdminNotAuthorizedForRestaurant_shouldReturn403Forbidden() throws Exception {
        Long restaurantId = 1L;
        String updaterUsername = "another_resto_admin";

        updateDto.setName("Attempted Update by Wrong Owner");

        when(restaurantSecurityServiceImpl.isRestaurantAdminForRestaurant(eq(restaurantId), eq(updaterUsername)))
                .thenReturn(false);

        mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());

        verify(restaurantSecurityServiceImpl).isRestaurantAdminForRestaurant(eq(restaurantId), eq(updaterUsername));
        verify(restaurantService, never()).updateRestaurant(anyLong(), any(UpdateRestaurantRequestDto.class));
    }


    @Test
    @DisplayName("PUT /restaurants/{id} - Not Found (ADMIN)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateRestaurant_whenAdminAndNotFound_shouldReturn404() throws Exception {
        Long nonExistentId = 99L;
        updateDto.setName("Some Update"); // ensure DTO is not null
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
        invalidUpdateDto.setName(""); // Assuming name @NotBlank or @NotEmpty

        ResultActions resultActions = mockMvc.perform(put("/api/v1/restaurants/{id}", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidUpdateDto)));

        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                // Adjust this based on your actual validation message and ErrorResponse structure
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