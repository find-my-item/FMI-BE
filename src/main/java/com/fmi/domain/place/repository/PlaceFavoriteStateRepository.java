package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.FavoritePlaceCandidate;
import java.time.LocalDateTime;
import java.util.List;
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

    private static final String FIND_FAVORITES = """
            SELECT pf.place_id, pf.updated_at
            FROM place_favorite pf
            JOIN place p ON p.id = pf.place_id
            WHERE pf.user_id = ?
              AND pf.is_favorite = TRUE
              AND pf.entity_status = 'ACTIVE'
              AND p.entity_status = 'ACTIVE'
            ORDER BY pf.updated_at DESC
            """;

    private static final String FIND_FAVORITES_AFTER = """
            SELECT pf.place_id, pf.updated_at
            FROM place_favorite pf
            JOIN place p ON p.id = pf.place_id
            WHERE pf.user_id = ?
              AND pf.is_favorite = TRUE
              AND pf.entity_status = 'ACTIVE'
              AND p.entity_status = 'ACTIVE'
              AND pf.updated_at < ?
            ORDER BY pf.updated_at DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public void save(Long placeId, Long userId) {
        jdbcTemplate.update(SAVE_FAVORITE, placeId, userId);
    }

    public void cancel(Long placeId, Long userId) {
        jdbcTemplate.update(CANCEL_FAVORITE, placeId, userId);
    }

    public List<FavoritePlaceCandidate> findFavorites(Long userId, LocalDateTime lastFavoriteUpdatedAt) {
        if (lastFavoriteUpdatedAt == null) {
            return jdbcTemplate.query(
                    FIND_FAVORITES,
                    (resultSet, rowNumber) -> new FavoritePlaceCandidate(
                            resultSet.getLong("place_id"),
                            resultSet.getTimestamp("updated_at").toLocalDateTime()),
                    userId);
        }
        return jdbcTemplate.query(
                FIND_FAVORITES_AFTER,
                (resultSet, rowNumber) -> new FavoritePlaceCandidate(
                        resultSet.getLong("place_id"),
                        resultSet.getTimestamp("updated_at").toLocalDateTime()),
                userId,
                lastFavoriteUpdatedAt);
    }
}
