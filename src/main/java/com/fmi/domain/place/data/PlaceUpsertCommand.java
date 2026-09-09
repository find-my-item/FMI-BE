package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PlaceUpsertCommand(
        String name,
        String address,
        double latitude,
        double longitude,
        String station,
        int stationDistanceMeters,
        PlaceType type,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        List<PlaceDailySchedule> dailySchedules) {

    public PlaceUpsertCommand {
        if (dailySchedules != null) {
            dailySchedules = Collections.unmodifiableList(new ArrayList<>(dailySchedules));
        }
    }
}
