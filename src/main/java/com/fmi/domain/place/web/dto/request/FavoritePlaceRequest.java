package com.fmi.domain.place.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoritePlaceRequest {

    private LocalDateTime lastFavoriteUpdatedAt;

    @Min(1) @Max(20) private int size = 20;
}
