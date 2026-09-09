package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {}
