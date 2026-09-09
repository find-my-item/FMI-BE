package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.PlaceFavorite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceFavoriteRepository extends JpaRepository<PlaceFavorite, Long> {
    Optional<PlaceFavorite> findByUserIdAndPlaceId(Long userId, Long placeId);
}
