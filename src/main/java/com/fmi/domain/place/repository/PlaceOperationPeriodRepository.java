package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.PlaceOperationPeriod;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceOperationPeriodRepository extends JpaRepository<PlaceOperationPeriod, Long> {
    Optional<PlaceOperationPeriod> findByPlaceId(Long placeId);
}
