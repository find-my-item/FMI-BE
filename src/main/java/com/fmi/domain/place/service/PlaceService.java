package com.fmi.domain.place.service;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.PlaceManagementDetail;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceOperationState;
import com.fmi.domain.place.data.PlaceSummary;
import com.fmi.domain.place.data.PlaceUpsertCommand;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.domain.place.repository.PlaceFavoriteRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.domain.place.service.internal.PlaceBusinessHourUpdater;
import com.fmi.domain.place.service.internal.PlaceOperationStatusCalculator;
import com.fmi.domain.place.service.internal.PlaceValidator;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.global.dto.UploadedImage;
import com.fmi.global.service.S3Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceFavoriteRepository placeFavoriteRepository;
    private final UserRepository userRepository;
    private final PlaceValidator placeValidator;
    private final PlaceBusinessHourUpdater placeBusinessHourUpdater;
    private final PlaceOperationStatusCalculator placeOperationStatusCalculator;
    private final S3Service s3Service;
    private final Clock clock;

    @Transactional
    public Long create(PlaceUpsertCommand command, MultipartFile thumbnail) {
        PlaceOperationPeriod operationPeriod = command.type() == PlaceType.POPUP
                ? PlaceOperationPeriod.builder()
                        .startDate(command.operationStartDate())
                        .endDate(command.operationEndDate())
                        .build()
                : null;
        placeValidator.validate(command.type(), operationPeriod, command.dailySchedules());
        UploadedImage uploadedThumbnail =
                s3Service.uploadWithThumbnail(List.of(thumbnail)).get(0);

        Place place = Place.builder()
                .name(command.name())
                .address(command.address())
                .latitude(command.latitude())
                .longitude(command.longitude())
                .station(command.station())
                .stationDistanceMeters(command.stationDistanceMeters())
                .type(command.type())
                .thumbnailUrl(uploadedThumbnail.thumbnailUrl())
                .operationPeriod(operationPeriod)
                .build();
        placeBusinessHourUpdater.update(place, command.dailySchedules(), LocalDateTime.now(clock));
        return placeRepository.save(place).getId();
    }

    public PlaceManagementDetail getForManagement(Long placeId) {
        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new GeneralException(PlaceErrorStatus.NOT_FOUND));
        if (place.isDeleted()) {
            throw new GeneralException(PlaceErrorStatus.NOT_FOUND);
        }

        PlaceOperationPeriod operationPeriod = place.getOperationPeriod();
        return new PlaceManagementDetail(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getStation(),
                place.getStationDistanceMeters(),
                place.getType(),
                place.getThumbnailUrl(),
                operationPeriod == null ? null : operationPeriod.getStartDate(),
                operationPeriod == null ? null : operationPeriod.getEndDate(),
                place.dailySchedules());
    }

    public List<PlaceSummary> getHomePlaces(PlaceType type, String userEmail) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> placeIds = placeRepository.findHomePlaceIds(type, now, PageRequest.of(0, 5));
        if (placeIds.isEmpty()) {
            return List.of();
        }

        List<Place> places = placeRepository.findAllWithSchedulesByIdIn(placeIds);
        places.sort(Comparator.comparingInt(place -> placeIds.indexOf(place.getId())));
        Set<Long> favoritePlaceIds = new HashSet<>();
        if (userEmail != null) {
            userRepository
                    .findByEmail(userEmail)
                    .ifPresent(user -> favoritePlaceIds.addAll(
                            placeFavoriteRepository.findFavoritePlaceIds(user.getId(), placeIds)));
        }

        return places.stream()
                .map(place -> {
                    PlaceOperationPeriod operationPeriod = place.getOperationPeriod();
                    PlaceOperationState operationState = placeOperationStatusCalculator.calculate(
                            place.getType(), operationPeriod, place.dailySchedules(), now);
                    return new PlaceSummary(
                            place.getId(),
                            place.getName(),
                            place.getAddress(),
                            place.getLatitude(),
                            place.getLongitude(),
                            place.getStation(),
                            place.getStationDistanceMeters(),
                            place.getType(),
                            place.getThumbnailUrl(),
                            operationPeriod == null ? null : operationPeriod.getStartDate(),
                            operationPeriod == null ? null : operationPeriod.getEndDate(),
                            operationState,
                            favoritePlaceIds.contains(place.getId()));
                })
                .toList();
    }

    @Transactional
    public void update(Long placeId, PlaceUpsertCommand command, MultipartFile newThumbnail) {
        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new GeneralException(PlaceErrorStatus.NOT_FOUND));
        if (place.isDeleted()) {
            throw new GeneralException(PlaceErrorStatus.NOT_FOUND);
        }
        placeValidator.validateType(place.getType(), command.type());

        PlaceOperationPeriod requestedPeriod = command.type() == PlaceType.POPUP
                ? PlaceOperationPeriod.builder()
                        .startDate(command.operationStartDate())
                        .endDate(command.operationEndDate())
                        .build()
                : null;
        placeValidator.validate(command.type(), requestedPeriod, command.dailySchedules());
        String thumbnailUrl = place.getThumbnailUrl();
        if (newThumbnail != null) {
            thumbnailUrl =
                    s3Service.uploadWithThumbnail(List.of(newThumbnail)).get(0).thumbnailUrl();
        }

        if (place.getType() == PlaceType.POPUP) {
            place.getOperationPeriod().revise(command.operationStartDate(), command.operationEndDate());
        }
        place.update(
                command.name(),
                command.address(),
                command.latitude(),
                command.longitude(),
                command.station(),
                command.stationDistanceMeters(),
                thumbnailUrl,
                place.getOperationPeriod());
        placeBusinessHourUpdater.update(place, command.dailySchedules(), LocalDateTime.now(clock));
    }

    @Transactional
    public void delete(Long placeId) {
        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new GeneralException(PlaceErrorStatus.NOT_FOUND));
        if (!place.delete(LocalDateTime.now(clock))) {
            throw new GeneralException(PlaceErrorStatus.ALREADY_DELETED);
        }
    }

    @Transactional
    public void restore(Long placeId) {
        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new GeneralException(PlaceErrorStatus.NOT_FOUND));
        if (!place.restore()) {
            throw new GeneralException(PlaceErrorStatus.ALREADY_ACTIVE);
        }
    }
}
