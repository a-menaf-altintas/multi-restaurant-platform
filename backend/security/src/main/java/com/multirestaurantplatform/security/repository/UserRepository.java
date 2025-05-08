package com.multirestaurantplatform.security.repository;

import com.multirestaurantplatform.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the User entity.
 */
@Repository // Indicates this is a Spring bean and provides exception translation
public interface UserRepository extends JpaRepository<User, Long> { // <EntityType, IdType>

    // --- Spring Data JPA Query Methods ---
    // Implementations are automatically generated based on method names.

    /**
     * Finds a user by their username. Spring Data JPA generates the query.
     * Consider if username search should be case-insensitive based on requirements.
     * @param username The username to search for.
     * @return An Optional containing the found User or empty if not found.
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their email address. Spring Data JPA generates the query.
     * Consider if email search should be case-insensitive.
     * @param email The email address to search for.
     * @return An Optional containing the found User or empty if not found.
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user exists with the given username.
     * More efficient than findByUsername().isPresent().
     * @param username The username to check.
     * @return true if a user with the username exists, false otherwise.
     */
    boolean existsByUsername(String username);

    /**
     * Checks if a user exists with the given email address.
     * More efficient than findByEmail().isPresent().
     * @param email The email address to check.
     * @return true if a user with the email exists, false otherwise.
     */
    boolean existsByEmail(String email);

    // We can add more complex queries using @Query annotation later if needed.
}