package com.fmi.domain.place.data;

import com.fmi.global.data.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceBusinessHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    @Embedded
    private PlaceTimeRange timeRange;

    @Builder
    private PlaceBusinessHour(DayOfWeek dayOfWeek, boolean closed, PlaceTimeRange timeRange) {
        this.dayOfWeek = dayOfWeek;
        this.closed = closed;
        this.timeRange = timeRange;
    }

    void assignPlace(Place place) {
        this.place = place;
    }

    public void reviseClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean hasSameRange(DayOfWeek dayOfWeek, PlaceTimeRange timeRange) {
        return this.dayOfWeek == dayOfWeek && this.timeRange.equals(timeRange);
    }

    public boolean delete(LocalDateTime deletedAt) {
        return super.delete(deletedAt);
    }

    public boolean restore() {
        return super.active();
    }
}
