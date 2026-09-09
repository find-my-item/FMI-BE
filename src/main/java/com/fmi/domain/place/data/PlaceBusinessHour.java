package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.global.data.BaseEntity;
import jakarta.persistence.Column;
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
import java.time.LocalTime;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PlaceBusinessHourType type;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder
    private PlaceBusinessHour(
            DayOfWeek dayOfWeek, boolean closed, PlaceBusinessHourType type, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.closed = closed;
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    void assignPlace(Place place) {
        this.place = place;
    }

    public void reviseClosed(boolean closed) {
        this.closed = closed;
    }
}
