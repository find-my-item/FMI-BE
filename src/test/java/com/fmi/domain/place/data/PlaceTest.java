package com.fmi.domain.place.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 애그리거트")
class PlaceTest {
    @Test
    @DisplayName("운영기간을 장소에 연결한다")
    void assignsOperationPeriod() {
        PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now())
                .build();
        Place place = builder().type(PlaceType.POPUP).operationPeriod(period).build();
        assertThat(place.getOperationPeriod()).isSameAs(period);
    }

    @Test
    @DisplayName("수정해도 장소 유형은 변경되지 않는다")
    void keepsTypeOnRevision() {
        Place place = builder().build();
        place.update("New name", "Address", 37.5, 127.0, "Station", 0, "thumb", null);
        assertThat(place.getType()).isEqualTo(PlaceType.CAFE);
    }

    @Test
    @DisplayName("장소를 soft delete하고 복구할 수 있다")
    void supportsSoftDeleteAndRestore() {
        Place place = builder().build();
        assertThat(place.delete(LocalDate.of(2026, 1, 1).atStartOfDay())).isTrue();
        assertThat(place.isDeleted()).isTrue();
        assertThat(place.restore()).isTrue();
        assertThat(place.isDeleted()).isFalse();
    }

    private static Place.PlaceBuilder builder() {
        return Place.builder()
                .name("Cafe")
                .address("Address")
                .latitude(37.5)
                .longitude(127.0)
                .station("Seongsu")
                .stationDistanceMeters(100)
                .type(PlaceType.CAFE)
                .thumbnailUrl("thumb");
    }
}
