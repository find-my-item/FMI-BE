package com.fmi.global.data;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_status", nullable = false)
    private EntityStatus entityStatus = EntityStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean delete(LocalDateTime deletedAt) {
        if (!isActive() || deletedAt == null) {
            return false;
        }

        this.entityStatus = EntityStatus.DELETED;
        this.deletedAt = deletedAt;
        return true;
    }

    public boolean active() {
        if (!isDeleted()) {
            return false;
        }

        this.entityStatus = EntityStatus.ACTIVE;
        this.deletedAt = null;
        return true;
    }

    public boolean isActive() {
        return entityStatus == EntityStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return entityStatus == EntityStatus.DELETED;
    }
}
