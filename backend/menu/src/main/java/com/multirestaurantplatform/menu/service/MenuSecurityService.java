// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/service/MenuSecurityService.java
package com.multirestaurantplatform.menu.service;

public interface MenuSecurityService {

    /**
     * Checks if the currently authenticated user (identified by username) has permission
     * to manage the specified menu.
     * This is typically used by RESTAURANT_ADMIN to ensure they only manage menus
     * belonging to restaurants they administer.
     *
     * @param menuId The ID of the menu to check.
     * @param username The username of the authenticated user (typically from `principal.username`).
     * @return true if the user is authorized to manage the menu, false otherwise.
     */
    boolean canManageMenu(Long menuId, String username);
}
