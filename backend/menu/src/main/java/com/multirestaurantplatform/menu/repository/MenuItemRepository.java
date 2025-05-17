package com.multirestaurantplatform.menu.repository;

import com.multirestaurantplatform.menu.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByMenuId(Long menuId);
    List<MenuItem> findByMenuIdAndIsActiveTrue(Long menuId);
    Optional<MenuItem> findByMenuIdAndNameIgnoreCase(Long menuId, String name);
}