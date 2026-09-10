package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.enums.PlaceType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepositoryCustom {
    private static final String FIND_HOME_CANDIDATE_IDS = """
            SELECT id
            FROM place
            WHERE entity_status = 'ACTIVE'
            ORDER BY created_at DESC, id DESC
            """;

    private static final String FIND_HOME_CANDIDATE_IDS_BY_TYPE = """
            SELECT id
            FROM place
            WHERE entity_status = 'ACTIVE'
              AND type = ?
            ORDER BY created_at DESC, id DESC
            """;

    private static final String FIND_MAP_CANDIDATE_IDS = """
            SELECT id
            FROM place
            WHERE entity_status = 'ACTIVE'
              AND type = ?
              AND latitude BETWEEN ? AND ?
              AND longitude BETWEEN ? AND ?
            ORDER BY ST_Distance_Sphere(
                        POINT(longitude, latitude),
                        POINT(?, ?)
                     ) ASC,
                     id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Long> findHomeCandidateIds(PlaceType type) {
        if (type == null) {
            return jdbcTemplate.queryForList(FIND_HOME_CANDIDATE_IDS, Long.class);
        }
        return jdbcTemplate.queryForList(FIND_HOME_CANDIDATE_IDS_BY_TYPE, Long.class, type.name());
    }

    @Override
    public List<Long> findMapCandidateIds(
            PlaceType type,
            double latitude,
            double longitude,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude) {
        return jdbcTemplate.queryForList(
                FIND_MAP_CANDIDATE_IDS,
                Long.class,
                type.name(),
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                longitude,
                latitude);
    }
}
