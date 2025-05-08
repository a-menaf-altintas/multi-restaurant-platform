package com.multirestaurantplatform.common.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@MappedSuperclass // Specifies that this is a base class for entities and its fields should be mapped to the columns of the inheriting entity's table.
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L; // Recommended for Serializable classes

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrementing ID strategy suitable for PostgreSQL/H2
    private Long id;

    @CreationTimestamp // Automatically set the timestamp when the entity is first persisted
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp // Automatically update the timestamp when the entity is updated
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- Optional: hashCode() and equals() based on ID ---
    // Useful for JPA entity comparisons, especially within collections.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        // Use ID for equality check if it's not null, otherwise rely on object identity
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // Use getClass().hashCode() to ensure consistency across different entity types
        return id != null ? Objects.hash(getClass().hashCode(), id) : super.hashCode();
        // Or simply: return getClass().hashCode(); // If ID is sufficient for hash-based collections before persistence
    }
}