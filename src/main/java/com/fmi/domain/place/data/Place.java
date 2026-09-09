package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.global.data.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 100)
    private String station;

    @Column(name = "station_distance_meters", nullable = false)
    private int stationDistanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PlaceType type;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL)
    private PlaceOperationPeriod operationPeriod;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL)
    private List<PlaceBusinessHour> businessHours = new ArrayList<>();

    @Builder
    private Place(
            String name,
            String address,
            double latitude,
            double longitude,
            String station,
            int stationDistanceMeters,
            PlaceType type,
            String thumbnailUrl,
            PlaceOperationPeriod operationPeriod) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.station = station;
        this.stationDistanceMeters = stationDistanceMeters;
        this.type = type;
        this.thumbnailUrl = thumbnailUrl;
        setOperationPeriod(operationPeriod);
    }

    public void update(
            String name,
            String address,
            double latitude,
            double longitude,
            String station,
            int stationDistanceMeters,
            String thumbnailUrl,
            PlaceOperationPeriod operationPeriod) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.station = station;
        this.stationDistanceMeters = stationDistanceMeters;
        this.thumbnailUrl = thumbnailUrl;
        setOperationPeriod(operationPeriod);
    }

    public boolean delete(LocalDateTime deletedAt) {
        return super.delete(deletedAt);
    }

    public boolean restore() {
        return super.active();
    }

    public void addBusinessHour(PlaceBusinessHour businessHour) {
        businessHour.assignPlace(this);
        businessHours.add(businessHour);
    }

    public List<PlaceDailySchedule> dailySchedules() {
        List<PlaceBusinessHour> activeBusinessHours =
                businessHours.stream().filter(PlaceBusinessHour::isActive).toList();
        return List.of(DayOfWeek.values()).stream()
                .map(dayOfWeek -> {
                    List<PlaceBusinessHour> hours = activeBusinessHours.stream()
                            .filter(businessHour -> businessHour.getDayOfWeek() == dayOfWeek)
                            .toList();
                    return new PlaceDailySchedule(
                            dayOfWeek,
                            hours.get(0).isClosed(),
                            hours.stream().map(PlaceBusinessHour::getTimeRange).toList());
                })
                .toList();
    }

    private void setOperationPeriod(PlaceOperationPeriod operationPeriod) {
        this.operationPeriod = operationPeriod;
        if (operationPeriod != null) {
            operationPeriod.assignPlace(this);
        }
    }
}
