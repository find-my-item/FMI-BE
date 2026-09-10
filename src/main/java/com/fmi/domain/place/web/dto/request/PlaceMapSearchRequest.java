package com.fmi.domain.place.web.dto.request;

import com.fmi.domain.place.data.enums.PlaceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceMapSearchRequest {

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") private Double latitude;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") private Double longitude;

    @Min(1) @Max(8) private int level = 6;

    @NotNull private PlaceType type;
}
