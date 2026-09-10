package com.fmi.domain.place.data;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PlaceDailySchedule(DayOfWeek dayOfWeek, boolean closed, List<PlaceTimeRange> timeRanges) {

    public PlaceDailySchedule {
        timeRanges = timeRanges == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(timeRanges));
    }
}
