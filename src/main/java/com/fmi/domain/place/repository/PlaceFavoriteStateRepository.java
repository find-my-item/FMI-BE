package com.fmi.domain.place.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaceFavoriteStateRepository {
    private static final String SAVE_FAVORITE = """
            INSERT INTO place_favorite (
                place_id,
                user_id,
                is_favorite,
                entity_status,
                created_at,
                updated_at
            )
            VALUES (?, ?, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                updated_at = IF(
                    is_favorite = FALSE OR entity_status <> 'ACTIVE',
                    CURRENT_TIMESTAMP(6),
                    updated_at
                ),
                is_favorite = TRUE,
                entity_status = 'ACTIVE',
                deleted_at = NULL
            """;

    private static final String CANCEL_FAVORITE = """
            UPDATE place_favorite
            SET updated_at = IF(is_favorite = TRUE, CURRENT_TIMESTAMP(6), updated_at),
                is_favorite = FALSE
            WHERE place_id = ?
              AND user_id = ?
              AND entity_status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public void save(Long placeId, Long userId) {
        jdbcTemplate.update(SAVE_FAVORITE, placeId, userId);
    }

    public void cancel(Long placeId, Long userId) {
        jdbcTemplate.update(CANCEL_FAVORITE, placeId, userId);
    }
}
