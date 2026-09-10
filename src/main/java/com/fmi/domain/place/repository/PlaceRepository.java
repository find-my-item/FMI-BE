package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.Place;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long>, PlaceRepositoryCustom {

    @EntityGraph(attributePaths = {"operationPeriod", "businessHours"})
    @Query("select distinct p from Place p where p.id in :placeIds")
    List<Place> findAllWithSchedulesByIdIn(@Param("placeIds") List<Long> placeIds);
}
