package com.fmi.domain.map.service;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.map.enums.MapLevel;
import com.fmi.domain.map.exception.MapErrorStatus;
import com.fmi.domain.map.web.dto.request.LocationMapPostRequest;
import com.fmi.domain.map.web.dto.request.MapPostRequest;
import com.fmi.domain.map.web.dto.request.PostMarkerRequest;
import com.fmi.domain.map.web.dto.request.RecentFoundPostRequest;
import com.fmi.domain.map.web.dto.response.*;
import com.fmi.domain.post.data.Post;
import com.fmi.domain.post.data.PostStatus;
import com.fmi.domain.post.data.PostType;
import com.fmi.domain.post.repository.PostRepository;
import com.fmi.domain.post.service.HotPostService;
import com.fmi.domain.post.service.PostQueryService;
import com.fmi.domain.user.data.User;
import com.fmi.domain.userblock.repository.BlockedUserRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.service.UserQueryService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostMapService {
    private static final int NEARBY_POST_RADIUS_METERS = 500;
    private static final MapLevel NEARBY_POST_PREFILTER_MAP_LEVEL = MapLevel.LEVEL_5;

    private final UserQueryService userQueryService;
    private final PostRepository postRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final PostQueryService postQueryService;
    private final HotPostService hotPostService;

    @Transactional(readOnly = true)
    public List<PostMarkerResponse> getPostMaker(PostMarkerRequest request, UserDetails userDetails) {
        MapLevel mapLevel = MapLevel.from(request.level());

        User user = userQueryService.findUserIfNullReturnNull(userDetails);

        Set<Long> excludedUserIds = getExcludedUserIds(user);

        return postRepository.findPostMarker(request.latitude(), request.longitude(), mapLevel, excludedUserIds);
    }

    @Transactional(readOnly = true)
    public List<RecentFoundPostResponse> getRecentFoundPosts(RecentFoundPostRequest request, UserDetails userDetails) {
        MapLevel mapLevel = MapLevel.from(request.level());

        User user = userQueryService.findUserIfNullReturnNull(userDetails);

        Set<Long> excludedUserIds = getExcludedUserIds(user);

        return postRepository.findRecentFoundPostList(
                request.latitude(), request.longitude(), mapLevel, excludedUserIds);
    }

    @Transactional(readOnly = true)
    public MapPostPageResponse getPostMap(
            Long postId, MapPostRequest request, Double lastDistance, Long lastPostId, UserDetails userDetails) {
        MapLevel mapLevel = MapLevel.from(request.level());

        User user = userQueryService.findUserIfNullReturnNull(userDetails);
        Post post = postQueryService.findById(postId);

        Set<Long> excludedUserIds = getExcludedUserIds(user);

        Set<Long> hotPostIds = hotPostService.resolveHotPostIdsForUser(
                request.postType(),
                request.postStatus(),
                request.category(),
                post.getAddress(),
                user != null ? user.getId() : null,
                20);

        return postRepository.findMapPosts(
                post.getLatitude(),
                post.getLongitude(),
                mapLevel,
                request.postType(),
                request.postStatus(),
                request.category(),
                request.keyword(),
                Objects.nonNull(user) ? user.getId() : null,
                excludedUserIds,
                hotPostIds,
                null,
                lastDistance,
                lastPostId);
    }

    @Transactional(readOnly = true)
    public LocationMapPostPageResponse searchPostsByLocation(
            LocationMapPostRequest request, Double lastDistance, Long lastPostId, UserDetails userDetails) {
        MapLevel mapLevel = MapLevel.from(request.level());

        User user = userQueryService.findUserIfNullReturnNull(userDetails);
        Set<Long> excludedUserIds = getExcludedUserIds(user);

        Set<Long> hotPostIds = hotPostService.resolveHotPostIdsForUser(
                request.postType(),
                request.postStatus(),
                request.category(),
                null,
                user != null ? user.getId() : null,
                20);

        return postRepository.searchMapPostsByLocation(
                request.latitude(),
                request.longitude(),
                mapLevel,
                request.postType(),
                request.postStatus(),
                request.category(),
                request.keyword(),
                Objects.nonNull(user) ? user.getId() : null,
                excludedUserIds,
                hotPostIds,
                lastDistance,
                lastPostId);
    }

    @Transactional(readOnly = true)
    public MapPostPageResponse getNearbyPosts(
            double latitude,
            double longitude,
            PostType postType,
            PostStatus postStatus,
            Category category,
            Double lastDistance,
            Long lastPostId,
            UserDetails userDetails) {
        if ((lastDistance == null) != (lastPostId == null) || lastDistance != null && lastDistance < 0) {
            throw new GeneralException(MapErrorStatus.CURSOR_INVALID);
        }
        User user = userQueryService.findUserIfNullReturnNull(userDetails);
        Set<Long> excludedUserIds = getExcludedUserIds(user);
        return postRepository.findMapPosts(
                latitude,
                longitude,
                NEARBY_POST_PREFILTER_MAP_LEVEL,
                postType,
                postStatus,
                category,
                null,
                user == null ? null : user.getId(),
                excludedUserIds,
                Set.of(),
                (double) NEARBY_POST_RADIUS_METERS,
                lastDistance,
                lastPostId);
    }

    @Transactional(readOnly = true)
    public List<PostMarkerResponse> getNearbyPostMarkers(
            double latitude,
            double longitude,
            PostType postType,
            PostStatus postStatus,
            Category category,
            UserDetails userDetails) {
        User user = userQueryService.findUserIfNullReturnNull(userDetails);
        Set<Long> excludedUserIds = getExcludedUserIds(user);
        return postRepository.findNearbyPostMarkers(
                latitude,
                longitude,
                NEARBY_POST_PREFILTER_MAP_LEVEL,
                (double) NEARBY_POST_RADIUS_METERS,
                postType,
                postStatus,
                category,
                excludedUserIds);
    }

    private Set<Long> getExcludedUserIds(User user) {
        if (user == null) return Set.of();
        Set<Long> excluded = new HashSet<>();
        excluded.addAll(blockedUserRepository.findBlockedUserIdsByBlockerId(user.getId()));
        excluded.addAll(blockedUserRepository.findBlockerIdsByBlockedId(user.getId()));
        return excluded;
    }
}
