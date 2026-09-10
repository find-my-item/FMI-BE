package com.fmi.domain.place.data;

import java.util.List;

public record PlaceMapSearchResult(List<PlaceSummary> places, int totalCount) {}
