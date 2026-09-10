package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.enums.PlaceType;
import java.util.List;

public interface PlaceRepositoryCustom {
    List<Long> findHomeCandidateIds(PlaceType type);

    List<Long> findMapCandidateIds(
            PlaceType type,
            double latitude,
            double longitude,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude);
}
