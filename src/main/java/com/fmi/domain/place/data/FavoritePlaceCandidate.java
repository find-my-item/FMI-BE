package com.fmi.domain.place.data;

import java.time.LocalDateTime;

public record FavoritePlaceCandidate(Long placeId, LocalDateTime favoriteUpdatedAt) {}
