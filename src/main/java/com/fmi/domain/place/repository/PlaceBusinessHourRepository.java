package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.PlaceBusinessHour;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceBusinessHourRepository extends JpaRepository<PlaceBusinessHour, Long> {
    List<PlaceBusinessHour> findAllByPlaceId(Long placeId);
}
