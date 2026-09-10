package com.fmi.domain.place.service;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.domain.place.repository.PlaceFavoriteStateRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceFavoriteService {

    private final PlaceService placeService;
    private final PlaceRepository placeRepository;
    private final PlaceFavoriteStateRepository placeFavoriteStateRepository;
    private final UserRepository userRepository;

    @Transactional
    public void save(Long placeId, String userEmail) {
        placeService.getPlaceSummary(placeId, null);
        User user = userRepository
                .findByEmail(userEmail)
                .orElseThrow(() -> new GeneralException(ErrorStatus._USER_NOT_FOUND));

        placeFavoriteStateRepository.save(placeId, user.getId());
    }

    @Transactional
    public void cancel(Long placeId, String userEmail) {
        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new GeneralException(PlaceErrorStatus.NOT_FOUND));
        if (place.isDeleted()) {
            throw new GeneralException(PlaceErrorStatus.NOT_FOUND);
        }
        User user = userRepository
                .findByEmail(userEmail)
                .orElseThrow(() -> new GeneralException(ErrorStatus._USER_NOT_FOUND));

        placeFavoriteStateRepository.cancel(placeId, user.getId());
    }
}
