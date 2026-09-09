package com.fmi.domain.place.repository;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query(value = """
                    SELECT p.id
                    FROM place p
                    LEFT JOIN place_operation_period op ON op.place_id = p.id
                    WHERE p.entity_status = 'ACTIVE'
                      AND (:#{#type?.name()} IS NULL OR p.type = :#{#type?.name()})
                      AND (
                        p.type <> 'POPUP'
                        OR :now < (
                          SELECT MAX(
                            TIMESTAMP(
                              DATE_SUB(
                                op.end_date,
                                INTERVAL MOD(
                                  DAYOFWEEK(op.end_date) - CASE bh.day_of_week
                                    WHEN 'SUNDAY' THEN 1 WHEN 'MONDAY' THEN 2 WHEN 'TUESDAY' THEN 3
                                    WHEN 'WEDNESDAY' THEN 4 WHEN 'THURSDAY' THEN 5 WHEN 'FRIDAY' THEN 6
                                    WHEN 'SATURDAY' THEN 7
                                  END + 7,
                                  7
                                ) DAY
                              ),
                              bh.end_time
                            ) + INTERVAL IF(bh.end_time <= bh.start_time, 1, 0) DAY
                          )
                          FROM place_business_hour bh
                          WHERE bh.place_id = p.id
                            AND bh.entity_status = 'ACTIVE'
                            AND bh.is_closed = 0
                            AND bh.type = 'BUSINESS'
                            AND DATE_SUB(
                              op.end_date,
                              INTERVAL MOD(
                                DAYOFWEEK(op.end_date) - CASE bh.day_of_week
                                  WHEN 'SUNDAY' THEN 1 WHEN 'MONDAY' THEN 2 WHEN 'TUESDAY' THEN 3
                                  WHEN 'WEDNESDAY' THEN 4 WHEN 'THURSDAY' THEN 5 WHEN 'FRIDAY' THEN 6
                                  WHEN 'SATURDAY' THEN 7
                                END + 7,
                                7
                              ) DAY
                            ) >= op.start_date
                        )
                      )
                    ORDER BY p.created_at DESC, p.id DESC
                    """, nativeQuery = true)
    List<Long> findHomePlaceIds(@Param("type") PlaceType type, @Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"operationPeriod", "businessHours"})
    @Query("select distinct p from Place p where p.id in :placeIds")
    List<Place> findAllWithSchedulesByIdIn(@Param("placeIds") List<Long> placeIds);
}
