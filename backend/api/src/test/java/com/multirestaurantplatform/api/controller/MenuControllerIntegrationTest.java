// File: backend/api/src/test/java/com/multirestaurantplatform/api/controller/MenuControllerIntegrationTest.java
package com.multirestaurantplatform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.multirestaurantplatform.menu.dto.CreateMenuRequestDto;
import com.multirestaurantplatform.menu.dto.MenuResponseDto;
import com.multirestaurantplatform.menu.dto.UpdateMenuRequestDto;
import com.multirestaurantplatform.menu.model.Menu;
import com.multirestaurantplatform.menu.repository.MenuRepository;
import com.multirestaurantplatform.restaurant.dto.CreateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.dto.RestaurantResponseDto;
import com.multirestaurantplatform.restaurant.dto.UpdateRestaurantRequestDto;
import com.multirestaurantplatform.restaurant.model.Restaurant;
import com.multirestaurantplatform.restaurant.repository.RestaurantRepository;
import com.multirestaurantplatform.security.dto.JwtAuthenticationResponse;
import com.multirestaurantplatform.security.dto.LoginRequest;
import com.multirestaurantplatform.security.dto.RegisterRequest;
import com.multirestaurantplatform.security.model.Role;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Ensures each test runs in a transaction and rolls back
@TestPropertySource(properties = {
        // Provide a dummy JWT secret for tests if your security config needs it for startup
        "app.jwt.secret=FZrx/+48fJdLdRjR7xESLZFrEbP/3gEUZhfyH9cG3mRWOGmzxZaEhyaZsSgjGCtUD2tKOuQUoqLXWosZbl9DTg==",
        "app.jwt.expiration-ms=3600000", // Example expiration
        "app.jwt.token-prefix=Bearer" // CORRECTED: Removed trailing space
})
public class MenuControllerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MenuControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuRepository menuRepository;

    // --- Test User Credentials ---
    private final String masterAdminUsername = "masteradmin_menu_test";
    private final String masterAdminPassword = "MasterAdminPass123!";
    private final String pizzaChefAdminUsername = "pizzachef_menu_test";
    private final String pizzaChefAdminPassword = "PizzaChefPass123!";
    private final String burgerBossAdminUsername = "burgerboss_menu_test";
    private final String burgerBossAdminPassword = "BurgerBossPass123!";
    private final String foodieSamUsername = "foodiesam_menu_test";
    private final String foodieSamPassword = "FoodieSamPass123!";

    private String masterAdminToken;
    private String pizzaChefAdminToken;
    private String burgerBossAdminToken;
    private String foodieSamToken;

    private Long masterAdminId;
    private Long pizzaChefAdminId;
    private Long burgerBossAdminId;

    private Long restaurantIdA; // Pizza Palace
    private Long restaurantIdB; // Burger Barn

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.registerModule(new JavaTimeModule());
        // Clear repositories to ensure clean state, though @Transactional should handle this
        menuRepository.deleteAll();
        // Ensure related entities are cleared correctly to avoid foreign key issues
        // If restaurant_admins is a join table without explicit entity,
        // deleting restaurants and users should handle it if cascading is set up or
        // if it's managed by the relationships.
        // For safety, you might clear restaurant_admins directly if it causes issues,
        // but typically clearing parent entities (Restaurant, User) is enough.
        restaurantRepository.deleteAll();
        userRepository.deleteAll();


        // --- Register Users ---
        masterAdminId = registerUser(masterAdminUsername, "master.admin.menu@example.com", masterAdminPassword, Set.of(Role.ADMIN));
        pizzaChefAdminId = registerUser(pizzaChefAdminUsername, "chef.pizza.menu@example.com", pizzaChefAdminPassword, Set.of(Role.RESTAURANT_ADMIN));
        burgerBossAdminId = registerUser(burgerBossAdminUsername, "boss.burger.menu@example.com", burgerBossAdminPassword, Set.of(Role.RESTAURANT_ADMIN));
        registerUser(foodieSamUsername, "sam.foodie.menu@example.com", foodieSamPassword, Set.of(Role.CUSTOMER));

        // --- Login Users ---
        masterAdminToken = loginUser(masterAdminUsername, masterAdminPassword);
        pizzaChefAdminToken = loginUser(pizzaChefAdminUsername, pizzaChefAdminPassword);
        burgerBossAdminToken = loginUser(burgerBossAdminUsername, burgerBossAdminPassword);
        foodieSamToken = loginUser(foodieSamUsername, foodieSamPassword);

        // --- Create Restaurants (as masterAdmin) ---
        restaurantIdA = createRestaurant("Pizza Palace Test", "Best pizza for testing.", masterAdminToken);
        restaurantIdB = createRestaurant("Burger Barn Test", "Juicy burgers for testing.", masterAdminToken);

        // --- Assign pizzaChefAdmin to Restaurant A (Pizza Palace) ---
        assignRestaurantAdmin(restaurantIdA, pizzaChefAdminId, masterAdminToken);
    }

    // --- Helper Methods ---
    private Long registerUser(String username, String email, String password, Set<Role> roles) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setEmail(email);
        registerRequest.setPassword(password);
        registerRequest.setRoles(roles);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("User not found after registration: " + username));
        return user.getId();
    }

    private String loginUser(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        JwtAuthenticationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), JwtAuthenticationResponse.class);
        return response.getAccessToken();
    }

    private Long createRestaurant(String name, String description, String adminToken) throws Exception {
        CreateRestaurantRequestDto createDto = new CreateRestaurantRequestDto();
        createDto.setName(name);
        createDto.setDescription(description);
        createDto.setAddress("123 Test St");
        createDto.setPhoneNumber("555-0100");
        createDto.setEmail(name.replaceAll("\\s+", "").toLowerCase() + "@test.com");

        MvcResult result = mockMvc.perform(post("/api/v1/restaurants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();
        RestaurantResponseDto response = objectMapper.readValue(result.getResponse().getContentAsString(), RestaurantResponseDto.class);
        return response.getId();
    }

    private void assignRestaurantAdmin(Long targetRestaurantId, Long userIdToAssign, String adminToken) throws Exception {
        UpdateRestaurantRequestDto updateDto = new UpdateRestaurantRequestDto();
        // Fetch the restaurant to get its current details to avoid nulling them out.
        // This makes the update more robust.
        Restaurant restaurant = restaurantRepository.findById(targetRestaurantId)
                .orElseThrow(() -> new IllegalStateException("Restaurant not found for assignment: " + targetRestaurantId));

        updateDto.setName(restaurant.getName());
        updateDto.setDescription(restaurant.getDescription());
        updateDto.setAddress(restaurant.getAddress());
        updateDto.setPhoneNumber(restaurant.getPhoneNumber());
        updateDto.setEmail(restaurant.getEmail());
        updateDto.setIsActive(restaurant.isActive());
        updateDto.setAdminUserIds(Set.of(userIdToAssign));


        mockMvc.perform(put("/api/v1/restaurants/" + targetRestaurantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());
    }

    private MenuResponseDto createMenuDirectly(String name, String description, Long targetRestaurantId, String token) throws Exception {
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto(name, description, targetRestaurantId);
        MvcResult result = mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                //.andExpect(status().isCreated()) // Temporarily comment this out or add .andDo(print())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        int statusCode = result.getResponse().getStatus(); // Get status code

        // Log the status code and response body
        log.info("createMenuDirectly ('{}') - Status: {}, Response Body: {}", name, statusCode, responseBody);

        // Only attempt to deserialize if the status is 201 Created
        if (statusCode == HttpStatus.CREATED.value()) {
            return objectMapper.readValue(responseBody, MenuResponseDto.class);
        } else {
            // If it's not 201, we can't deserialize to MenuResponseDto.
            // We might throw an exception or return null, depending on how tests should handle it.
            // For now, let's throw an assertion error to make it clear in the test output.
            fail("createMenuDirectly failed for menu '" + name + "' with status " + statusCode + ". Response: " + responseBody);
            return null; // Unreachable, but satisfies compiler
        }
    }


    // --- Test Cases for POST /api/v1/menus (Create Menu) ---

    @Test
    @DisplayName("POST /menus - ADMIN creates menu for Restaurant A - Success")
    void createMenu_asAdmin_forRestaurantA_shouldSucceed() throws Exception {
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto("Admin's Lunch Special", "Lunch by admin", restaurantIdA);

        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Admin's Lunch Special")))
                .andExpect(jsonPath("$.restaurantId", is(restaurantIdA.intValue())))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("POST /menus - PizzaChefAdmin creates menu for their Restaurant A - Success")
    void createMenu_asPizzaChefAdmin_forOwnRestaurantA_shouldSucceed() throws Exception {
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto("Pizza Chef's Dinner Menu", "Dinner by pizza chef", restaurantIdA);

        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + pizzaChefAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Pizza Chef's Dinner Menu")));
    }

    @Test
    @DisplayName("POST /menus - PizzaChefAdmin creates menu for unmanaged Restaurant B - Forbidden")
    void createMenu_asPizzaChefAdmin_forUnmanagedRestaurantB_shouldBeForbidden() throws Exception {
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto("Illegal Pizza Menu", "For Burger Barn", restaurantIdB);

        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + pizzaChefAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /menus - BurgerBossAdmin (unassigned) creates menu for Restaurant B - Forbidden")
    void createMenu_asBurgerBossAdmin_forRestaurantB_shouldBeForbidden() throws Exception {
        CreateMenuRequestDto createMenuDto = new CreateMenuRequestDto("Hopeful Burger Menu", "By unassigned boss", restaurantIdB);

        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + burgerBossAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMenuDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /menus - Name Conflict for same restaurant - Should return 409")
    void createMenu_withNameConflict_shouldReturn409() throws Exception {
        createMenuDirectly("Conflict Menu", "Desc1", restaurantIdA, masterAdminToken);
        CreateMenuRequestDto duplicateMenuDto = new CreateMenuRequestDto("Conflict Menu", "Desc2", restaurantIdA);

        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateMenuDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /menus - Invalid DTO (blank name) - Should return 400")
    void createMenu_withInvalidDto_shouldReturn400() throws Exception {
        CreateMenuRequestDto invalidDto = new CreateMenuRequestDto("", "Description", restaurantIdA);
        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /menus - Non-existent Restaurant ID - Should return 404")
    void createMenu_withNonExistentRestaurantId_shouldReturn404() throws Exception {
        CreateMenuRequestDto dto = new CreateMenuRequestDto("Menu for Ghost", "Desc", 9999L);
        mockMvc.perform(post("/api/v1/menus")
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }


    // --- Test Cases for GET /api/v1/menus/{menuId} ---
    @Test
    @DisplayName("GET /menus/{menuId} - Any Authenticated User - Success")
    void getMenuById_asAnyAuthenticatedUser_shouldSucceed() throws Exception {
        MenuResponseDto createdMenu = createMenuDirectly("Readable Menu", "Details", restaurantIdA, masterAdminToken);

        mockMvc.perform(get("/api/v1/menus/" + createdMenu.getId())
                        .header("Authorization", "Bearer " + foodieSamToken)) // Customer token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdMenu.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Readable Menu")));
    }

    @Test
    @DisplayName("GET /menus/{menuId} - Non-existent ID - Should return 404")
    void getMenuById_withNonExistentId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/menus/99999")
                        .header("Authorization", "Bearer " + masterAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /menus/{menuId} - Unauthenticated - Should return 401")
    void getMenuById_unauthenticated_shouldReturn401() throws Exception {
        MenuResponseDto createdMenu = createMenuDirectly("Public Menu Item", "Details", restaurantIdA, masterAdminToken);
        mockMvc.perform(get("/api/v1/menus/" + createdMenu.getId()))
                .andExpect(status().isUnauthorized());
    }


    // --- Test Cases for GET /api/v1/menus/by-restaurant/{restaurantId} ---
    @Test
    @DisplayName("GET /menus/by-restaurant/{restaurantId} - Success")
    void getMenusByRestaurantId_shouldReturnMenus() throws Exception {
        createMenuDirectly("Menu 1 for A", "Desc", restaurantIdA, masterAdminToken);
        createMenuDirectly("Menu 2 for A", "Desc", restaurantIdA, pizzaChefAdminToken); // Pizza chef adds to their own

        mockMvc.perform(get("/api/v1/menus/by-restaurant/" + restaurantIdA)
                        .header("Authorization", "Bearer " + foodieSamToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", isOneOf("Menu 1 for A", "Menu 2 for A")));
    }

    @Test
    @DisplayName("GET /menus/by-restaurant/{restaurantId} - No Menus - Success Empty List")
    void getMenusByRestaurantId_forRestaurantWithNoMenus_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/menus/by-restaurant/" + restaurantIdB) // Restaurant B has no menus yet
                        .header("Authorization", "Bearer " + foodieSamToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- Test Cases for GET /api/v1/menus/by-restaurant/{restaurantId}/active ---
    @Test
    @DisplayName("GET /menus/by-restaurant/{restaurantId}/active - Filters inactive menus")
    void getActiveMenusByRestaurantId_shouldFilterInactive() throws Exception {
        MenuResponseDto activeMenu = createMenuDirectly("Active Menu", "Desc", restaurantIdA, masterAdminToken);
        MenuResponseDto inactiveMenu = createMenuDirectly("Inactive Menu Initial", "Desc", restaurantIdA, masterAdminToken);

        // Make one menu inactive via update
        UpdateMenuRequestDto updateToInactive = new UpdateMenuRequestDto();
        updateToInactive.setIsActive(false);
        mockMvc.perform(put("/api/v1/menus/" + inactiveMenu.getId())
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateToInactive)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/menus/by-restaurant/" + restaurantIdA + "/active")
                        .header("Authorization", "Bearer " + foodieSamToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(activeMenu.getId().intValue())))
                .andExpect(jsonPath("$[0].name", is("Active Menu")));
    }


    // --- Test Cases for PUT /api/v1/menus/{menuId} ---
    @Test
    @DisplayName("PUT /menus/{menuId} - ADMIN updates menu - Success")
    void updateMenu_asAdmin_shouldSucceed() throws Exception {
        MenuResponseDto menu = createMenuDirectly("Original Name Admin", "Original Desc", restaurantIdA, masterAdminToken);
        UpdateMenuRequestDto updateDto = new UpdateMenuRequestDto("Updated Name by Admin", "Updated Desc", false);

        mockMvc.perform(put("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name by Admin")))
                .andExpect(jsonPath("$.description", is("Updated Desc")))
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    @DisplayName("PUT /menus/{menuId} - PizzaChefAdmin updates own restaurant's menu - Success")
    void updateMenu_asPizzaChefAdmin_forOwnMenu_shouldSucceed() throws Exception {
        MenuResponseDto menu = createMenuDirectly("Pizza Chef Original", "Desc", restaurantIdA, pizzaChefAdminToken);
        UpdateMenuRequestDto updateDto = new UpdateMenuRequestDto("Pizza Chef Updated", null, true); // Only update name and active

        mockMvc.perform(put("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + pizzaChefAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Pizza Chef Updated")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("PUT /menus/{menuId} - PizzaChefAdmin updates unmanaged menu - Forbidden")
    void updateMenu_asPizzaChefAdmin_forUnmanagedMenu_shouldBeForbidden() throws Exception {
        MenuResponseDto menuForB = createMenuDirectly("Menu for B by Admin", "Desc", restaurantIdB, masterAdminToken);
        UpdateMenuRequestDto updateDto = new UpdateMenuRequestDto("Illegal Update Attempt", null, null);

        mockMvc.perform(put("/api/v1/menus/" + menuForB.getId())
                        .header("Authorization", "Bearer " + pizzaChefAdminToken) // Pizza chef tries to update menu of Restaurant B
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /menus/{menuId} - Update with Name Conflict - Should return 409")
    void updateMenu_withNameConflict_shouldReturn409() throws Exception {
        MenuResponseDto menu1 = createMenuDirectly("Menu Alpha", "Desc", restaurantIdA, masterAdminToken);
        MenuResponseDto menu2 = createMenuDirectly("Menu Beta", "Desc", restaurantIdA, masterAdminToken);
        UpdateMenuRequestDto updateDto = new UpdateMenuRequestDto("Menu Beta", null, null); // Try to rename menu1 to menu2's name

        mockMvc.perform(put("/api/v1/menus/" + menu1.getId())
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /menus/{menuId} - Invalid DTO (e.g., blank name) - Should return 400")
    void updateMenu_withInvalidDto_shouldReturn400() throws Exception {
        MenuResponseDto menu = createMenuDirectly("Menu To Update With Invalid Data", "Desc", restaurantIdA, masterAdminToken);
        UpdateMenuRequestDto invalidUpdateDto = new UpdateMenuRequestDto("", "New Description", true); // Blank name

        mockMvc.perform(put("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + masterAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdateDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Ensure you expect JSON
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors", hasItem(containsString("Menu name must be between 2 and 100 characters"))));
    }


    // --- Test Cases for DELETE /api/v1/menus/{menuId} ---
    @Test
    @DisplayName("DELETE /menus/{menuId} - ADMIN deletes menu - Success (Soft Delete)")
    void deleteMenu_asAdmin_shouldSucceedAndSetInactive() throws Exception {
        MenuResponseDto menu = createMenuDirectly("To Be Deleted by Admin", "Desc", restaurantIdA, masterAdminToken);
        assertTrue(menu.isActive());

        mockMvc.perform(delete("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + masterAdminToken))
                .andExpect(status().isNoContent());

        // Verify it's inactive
        mockMvc.perform(get("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + masterAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    @DisplayName("DELETE /menus/{menuId} - PizzaChefAdmin deletes own restaurant's menu - Success (Soft Delete)")
    void deleteMenu_asPizzaChefAdmin_forOwnMenu_shouldSucceed() throws Exception {
        MenuResponseDto menu = createMenuDirectly("To Be Deleted by Pizza Chef", "Desc", restaurantIdA, pizzaChefAdminToken);

        mockMvc.perform(delete("/api/v1/menus/" + menu.getId())
                        .header("Authorization", "Bearer " + pizzaChefAdminToken))
                .andExpect(status().isNoContent());

        Menu updatedMenu = menuRepository.findById(menu.getId()).orElseThrow();
        assertFalse(updatedMenu.isActive());
    }

    @Test
    @DisplayName("DELETE /menus/{menuId} - PizzaChefAdmin deletes unmanaged menu - Forbidden")
    void deleteMenu_asPizzaChefAdmin_forUnmanagedMenu_shouldBeForbidden() throws Exception {
        MenuResponseDto menuForB = createMenuDirectly("Menu B for Delete Test", "Desc", restaurantIdB, masterAdminToken);

        mockMvc.perform(delete("/api/v1/menus/" + menuForB.getId())
                        .header("Authorization", "Bearer " + pizzaChefAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /menus/{menuId} - Menu Not Found - Should return 404")
    void deleteMenu_nonExistent_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/v1/menus/87654")
                        .header("Authorization", "Bearer " + masterAdminToken))
                .andExpect(status().isNotFound());
    }
}
