package com.fmi.domain.place.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 즐겨찾기")
class PlaceFavoriteTest {
    @Test
    @DisplayName("신규 즐겨찾기는 활성 상태다")
    void startsFavorite() {
        assertThat(PlaceFavorite.builder().userId(1L).placeId(2L).build().isFavorite())
                .isTrue();
    }

    @Test
    @DisplayName("즐겨찾기를 취소하고 다시 활성화한다")
    void togglesFavorite() {
        PlaceFavorite favorite = PlaceFavorite.builder().userId(1L).placeId(2L).build();
        assertThat(favorite.unfavorite()).isTrue();
        assertThat(favorite.unfavorite()).isFalse();
        assertThat(favorite.favorite()).isTrue();
        assertThat(favorite.favorite()).isFalse();
    }
}
