package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.PlaceFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceFavoriteRepository extends JpaRepository<PlaceFavorite, Long> {
    Optional<PlaceFavorite> findByUserIdAndPlaceId(Long userId, Long placeId);

    @Query(
            "select pf.placeId from PlaceFavorite pf where pf.userId = :userId and pf.placeId in :placeIds and pf.favorite = true")
    List<Long> findFavoritePlaceIds(@Param("userId") Long userId, @Param("placeIds") List<Long> placeIds);
}
