package com.fmi.domain.place.data;

import com.fmi.global.data.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "place_favorite")
public class PlaceFavorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Builder
    private PlaceFavorite(Long userId, Long placeId) {
        this.userId = userId;
        this.placeId = placeId;
        this.favorite = true;
    }

    public boolean favorite() {
        if (favorite) {
            return false;
        }
        favorite = true;
        return true;
    }

    public boolean unfavorite() {
        if (!favorite) {
            return false;
        }
        favorite = false;
        return true;
    }
}
