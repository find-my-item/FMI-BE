package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceOperationStatus;
import java.util.List;

public record PlaceOperationState(PlaceOperationStatus status, List<PlaceTimeRange> todayBusinessHours) {}
