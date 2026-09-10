package com.fmi.domain.map.repository;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.map.enums.MapLevel;
import com.fmi.domain.map.web.dto.response.*;
import com.fmi.domain.post.data.*;
import com.fmi.domain.postfavorite.data.QPostFavorite;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostMapCustomImpl implements PostMapCustom {
    private final JPAQueryFactory jpaQueryFactory;

    private static final int MAP_CARD_PAGE_SIZE = 10;

    @Override
    public List<PostMarkerResponse> findPostMarker(
            double lat, double lng, MapLevel mapLevel, Set<Long> excludedUserIds) {
        QPost p = QPost.post;
        double latDelta = mapLevel.getHalfHeightMeter() / 111_320.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        if (Math.abs(cosLat) < 1e-8) {
            cosLat = 1e-8;
        }
        double lngDelta = mapLevel.getHalfWidthMeter() / (111_320.0 * cosLat);

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        BooleanBuilder where = new BooleanBuilder()
                .and(p.deleted.isFalse())
                .and(p.latitude.isNotNull())
                .and(p.longitude.isNotNull())
                .and(p.latitude.between(minLat, maxLat))
                .and(p.longitude.between(minLng, maxLng))
                .and(p.temporarySave.isFalse());

        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            where.and(p.user.id.notIn(excludedUserIds));
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        PostMarkerResponse.class, p.id, p.latitude, p.longitude, p.category, p.postType, p.postStatus))
                .from(p)
                .where(where)
                .fetch();
    }

    @Override
    public List<PostMarkerResponse> findNearbyPostMarkers(
            double lat,
            double lng,
            MapLevel mapLevel,
            Double maxDistanceMeters,
            PostType postType,
            PostStatus postStatus,
            Category category,
            Set<Long> excludedUserIds) {
        QPost post = QPost.post;
        double latitudeDelta = mapLevel.getHalfHeightMeter() / 111_320.0;
        double longitudeScale = Math.max(Math.abs(Math.cos(Math.toRadians(lat))), 1e-8);
        double longitudeDelta = mapLevel.getHalfWidthMeter() / (111_320.0 * longitudeScale);
        NumberExpression<Double> distanceMeter = Expressions.numberTemplate(
                Double.class,
                "ST_Distance_Sphere(POINT({0},{1}), POINT({2},{3}))",
                post.longitude,
                post.latitude,
                lng,
                lat);

        BooleanBuilder where = new BooleanBuilder()
                .and(post.deleted.isFalse())
                .and(post.temporarySave.isFalse())
                .and(post.latitude.isNotNull())
                .and(post.longitude.isNotNull())
                .and(post.latitude.between(lat - latitudeDelta, lat + latitudeDelta))
                .and(post.longitude.between(lng - longitudeDelta, lng + longitudeDelta))
                .and(distanceMeter.loe(maxDistanceMeters));
        if (postType != null) {
            where.and(post.postType.eq(postType));
        }
        if (postStatus != null) {
            where.and(post.postStatus.eq(postStatus));
        }
        if (category != null) {
            where.and(post.category.eq(category));
        }
        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            where.and(post.user.id.notIn(excludedUserIds));
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        PostMarkerResponse.class,
                        post.id,
                        post.latitude,
                        post.longitude,
                        post.category,
                        post.postType,
                        post.postStatus))
                .from(post)
                .where(where)
                .orderBy(distanceMeter.asc(), post.id.desc())
                .limit(10)
                .fetch();
    }

    @Override
    public MapPostPageResponse findMapPosts(
            double lat,
            double lng,
            MapLevel mapLevel,
            PostType postType,
            PostStatus postStatus,
            Category category,
            String keyword,
            Long userId,
            Set<Long> excludedUserIds,
            Set<Long> hotPostIds,
            Double maxDistanceMeters,
            Double lastDistance,
            Long lastPostId) {

        QPost post = QPost.post;
        QPostFavorite postFavorite = QPostFavorite.postFavorite;
        QPostImage postImage = QPostImage.postImage;

        double latDelta = mapLevel.getHalfHeightMeter() / 111_320.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        if (Math.abs(cosLat) < 1e-8) {
            cosLat = 1e-8;
        }
        double lngDelta = mapLevel.getHalfWidthMeter() / (111_320.0 * cosLat);

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        NumberExpression<Double> distanceMeter = Expressions.numberTemplate(
                Double.class,
                "ST_Distance_Sphere(POINT({0},{1}), POINT({2},{3}))",
                post.longitude,
                post.latitude,
                lng,
                lat);

        BooleanBuilder where = new BooleanBuilder();
        where.and(post.deleted.isFalse());
        where.and(post.temporarySave.isFalse());
        where.and(post.latitude.isNotNull());
        where.and(post.longitude.isNotNull());
        where.and(post.latitude.between(minLat, maxLat));
        where.and(post.longitude.between(minLng, maxLng));

        if (postType != null) {
            where.and(post.postType.eq(postType));
        }
        if (postStatus != null) {
            where.and(post.postStatus.eq(postStatus));
        }
        if (category != null) {
            where.and(post.category.eq(category));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.and(post.title.containsIgnoreCase(keyword).or(post.content.containsIgnoreCase(keyword)));
        }

        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            where.and(post.user.id.notIn(excludedUserIds));
        }

        if (Objects.nonNull(lastDistance) && Objects.nonNull(lastPostId)) {
            where.and(distanceMeter
                    .gt(lastDistance)
                    .or(distanceMeter.eq(lastDistance).and(post.id.lt(lastPostId))));
        }
        if (maxDistanceMeters != null) {
            where.and(distanceMeter.loe(maxDistanceMeters));
        }

        List<Tuple> tuples = jpaQueryFactory
                .select(post, distanceMeter)
                .from(post)
                .where(where)
                .orderBy(distanceMeter.asc(), post.id.desc())
                .limit(MAP_CARD_PAGE_SIZE + 1)
                .fetch();

        boolean hasNext = tuples.size() > MAP_CARD_PAGE_SIZE;

        if (hasNext) {
            tuples = tuples.subList(0, MAP_CARD_PAGE_SIZE);
        }

        List<Post> posts = tuples.stream().map(tuple -> tuple.get(post)).toList();

        if (posts.isEmpty()) {
            return new MapPostPageResponse(List.of(), false, null, null);
        }

        Double nextDistance = null;
        Long nextPostId = null;

        if (hasNext) {
            Tuple lastTuple = tuples.get(tuples.size() - 1);
            nextDistance = lastTuple.get(distanceMeter);
            nextPostId = lastTuple.get(post).getId();
        }

        List<Long> postIdList = posts.stream().map(Post::getId).toList();

        // 목록 썸네일 우선, 없으면 원본 URL로 대체
        Expression<String> listThumbnailUrl = postImage.thumbnailUrl.coalesce(postImage.imgUrl);
        Map<Long, String> thumbnailMap = jpaQueryFactory
                .select(postImage.post.id, listThumbnailUrl)
                .from(postImage)
                .where(postImage.post.id.in(postIdList), postImage.imageType.eq(ImageType.THUMBNAIL))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postImage.post.id)),
                        t -> Objects.requireNonNull(t.get(listThumbnailUrl)),
                        (a, b) -> a));

        Map<Long, Long> favoriteCountMap = jpaQueryFactory
                .select(postFavorite.post.id, postFavorite.favorite_id.count())
                .from(postFavorite)
                .where(postFavorite.post.id.in(postIdList), postFavorite.isFavorite.isTrue())
                .groupBy(postFavorite.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postFavorite.post.id)),
                        t -> Objects.requireNonNull(t.get(postFavorite.favorite_id.count()))));

        Set<Long> myFavoritePostIds = userId == null
                ? Set.of()
                : new HashSet<>(jpaQueryFactory
                        .select(postFavorite.post.id)
                        .from(postFavorite)
                        .where(
                                postFavorite.user.id.eq(userId),
                                postFavorite.post.id.in(postIdList),
                                postFavorite.isFavorite.isTrue())
                        .fetch());

        Map<Long, Integer> imageCountMap = jpaQueryFactory
                .select(postImage.post.id, postImage.id.count())
                .from(postImage)
                .where(postImage.post.id.in(postIdList))
                .groupBy(postImage.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postImage.post.id)),
                        t -> Objects.requireNonNull(t.get(postImage.id.count())).intValue()));

        List<MapPostResponse> content = posts.stream()
                .map(p -> {
                    Long pid = p.getId();
                    boolean isHot = hotPostIds != null && hotPostIds.contains(pid);

                    return new MapPostResponse(
                            pid,
                            p.getTitle(),
                            p.makeSummary(),
                            thumbnailMap.get(pid),
                            p.getAddress(),
                            p.getPostStatus(),
                            p.getPostType(),
                            p.getCategory(),
                            favoriteCountMap.getOrDefault(pid, 0L),
                            myFavoritePostIds.contains(pid),
                            p.getViewCount(),
                            p.isNew(),
                            isHot,
                            p.getCreatedAt(),
                            imageCountMap.getOrDefault(pid, 0));
                })
                .toList();

        return new MapPostPageResponse(content, hasNext, nextDistance, nextPostId);
    }

    @Override
    public List<RecentFoundPostResponse> findRecentFoundPostList(
            double lat, double lng, MapLevel mapLevel, Set<Long> excludedUserIds) {
        QPost p = QPost.post;
        QPostImage pi = QPostImage.postImage;

        double latDelta = mapLevel.getHalfHeightMeter() / 111_320.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        if (Math.abs(cosLat) < 1e-8) {
            cosLat = 1e-8;
        }
        double lngDelta = mapLevel.getHalfWidthMeter() / (111_320.0 * cosLat);

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        BooleanBuilder where = new BooleanBuilder()
                .and(p.deleted.isFalse())
                .and(p.temporarySave.isFalse())
                .and(p.postType.eq(PostType.FOUND))
                .and(p.postStatus.eq(PostStatus.SEARCHING))
                .and(p.latitude.isNotNull())
                .and(p.longitude.isNotNull())
                .and(p.latitude.between(minLat, maxLat))
                .and(p.longitude.between(minLng, maxLng));

        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            where.and(p.user.id.notIn(excludedUserIds));
        }

        return jpaQueryFactory
                .select(Projections.constructor(
                        RecentFoundPostResponse.class, p.id, p.title, pi.thumbnailUrl.coalesce(pi.imgUrl), p.createdAt))
                .from(p)
                .leftJoin(pi)
                .on(pi.post.eq(p), pi.imageType.eq(ImageType.THUMBNAIL))
                .where(where)
                .orderBy(p.createdAt.desc(), p.id.desc())
                .limit(10)
                .fetch();
    }

    @Override
    public LocationMapPostPageResponse searchMapPostsByLocation(
            double lat,
            double lng,
            MapLevel mapLevel,
            PostType postType,
            PostStatus postStatus,
            Category category,
            String keyword,
            Long userId,
            Set<Long> excludedUserIds,
            Set<Long> hotPostIds,
            Double lastDistance,
            Long lastPostId) {

        QPost post = QPost.post;
        QPostFavorite postFavorite = QPostFavorite.postFavorite;
        QPostImage postImage = QPostImage.postImage;

        double latDelta = mapLevel.getHalfHeightMeter() / 111_320.0;

        double cosLat = Math.cos(Math.toRadians(lat));
        if (Math.abs(cosLat) < 1e-8) {
            cosLat = 1e-8;
        }
        double lngDelta = mapLevel.getHalfWidthMeter() / (111_320.0 * cosLat);

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        NumberExpression<Double> distanceMeter = Expressions.numberTemplate(
                Double.class,
                "ST_Distance_Sphere(POINT({0},{1}), POINT({2},{3}))",
                post.longitude,
                post.latitude,
                lng,
                lat);

        BooleanBuilder geoOrKeyword = new BooleanBuilder();

        geoOrKeyword.or(post.latitude.between(minLat, maxLat).and(post.longitude.between(minLng, maxLng)));

        if (keyword != null && !keyword.isBlank()) {
            geoOrKeyword.or(post.address.containsIgnoreCase(keyword));
        }

        BooleanBuilder where = new BooleanBuilder();
        where.and(geoOrKeyword);
        where.and(post.latitude.isNotNull());
        where.and(post.longitude.isNotNull());
        where.and(post.deleted.isFalse());
        where.and(post.temporarySave.isFalse());

        if (postType != null) {
            where.and(post.postType.eq(postType));
        }
        if (postStatus != null) {
            where.and(post.postStatus.eq(postStatus));
        }
        if (category != null) {
            where.and(post.category.eq(category));
        }

        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            where.and(post.user.id.notIn(excludedUserIds));
        }

        if (lastDistance != null && lastPostId != null) {
            where.and(distanceMeter
                    .gt(lastDistance)
                    .or(distanceMeter.eq(lastDistance).and(post.id.lt(lastPostId))));
        }

        List<Tuple> tuples = jpaQueryFactory
                .select(post, distanceMeter)
                .from(post)
                .where(where)
                .orderBy(distanceMeter.asc(), post.id.desc())
                .limit(MAP_CARD_PAGE_SIZE + 1)
                .fetch();

        boolean hasNext = tuples.size() > MAP_CARD_PAGE_SIZE;
        if (hasNext) {
            tuples = tuples.subList(0, MAP_CARD_PAGE_SIZE);
        }

        List<Post> posts = tuples.stream().map(tuple -> tuple.get(post)).toList();

        if (posts.isEmpty()) {
            return new LocationMapPostPageResponse(List.of(), false, null, null);
        }

        Double nextDistance = null;
        Long nextPostId = null;

        if (hasNext) {
            Tuple lastTuple = tuples.get(tuples.size() - 1);
            nextDistance = lastTuple.get(distanceMeter);
            nextPostId = Objects.requireNonNull(lastTuple.get(post)).getId();
        }

        List<Long> postIdList = posts.stream().map(Post::getId).toList();

        // 목록 썸네일 우선, 없으면 원본 URL로 대체
        Expression<String> listThumbnailUrl = postImage.thumbnailUrl.coalesce(postImage.imgUrl);
        Map<Long, String> thumbnailMap = jpaQueryFactory
                .select(postImage.post.id, listThumbnailUrl)
                .from(postImage)
                .where(postImage.post.id.in(postIdList), postImage.imageType.eq(ImageType.THUMBNAIL))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postImage.post.id)),
                        t -> Objects.requireNonNull(t.get(listThumbnailUrl)),
                        (a, b) -> a));

        Map<Long, Long> favoriteCountMap = jpaQueryFactory
                .select(postFavorite.post.id, postFavorite.favorite_id.count())
                .from(postFavorite)
                .where(postFavorite.post.id.in(postIdList), postFavorite.isFavorite.isTrue())
                .groupBy(postFavorite.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postFavorite.post.id)),
                        t -> Objects.requireNonNull(t.get(postFavorite.favorite_id.count()))));

        Set<Long> myFavoritePostIds = userId == null
                ? Set.of()
                : new HashSet<>(jpaQueryFactory
                        .select(postFavorite.post.id)
                        .from(postFavorite)
                        .where(
                                postFavorite.user.id.eq(userId),
                                postFavorite.post.id.in(postIdList),
                                postFavorite.isFavorite.isTrue())
                        .fetch());

        Map<Long, Integer> imageCountMap = jpaQueryFactory
                .select(postImage.post.id, postImage.id.count())
                .from(postImage)
                .where(postImage.post.id.in(postIdList))
                .groupBy(postImage.post.id)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> Objects.requireNonNull(t.get(postImage.post.id)),
                        t -> Objects.requireNonNull(t.get(postImage.id.count())).intValue()));

        List<MapPostResponse> content = posts.stream()
                .map(p -> {
                    Long pid = p.getId();
                    boolean isHot = hotPostIds != null && hotPostIds.contains(pid);

                    return new MapPostResponse(
                            pid,
                            p.getTitle(),
                            p.makeSummary(),
                            thumbnailMap.get(pid),
                            p.getAddress(),
                            p.getPostStatus(),
                            p.getPostType(),
                            p.getCategory(),
                            favoriteCountMap.getOrDefault(pid, 0L),
                            myFavoritePostIds.contains(pid),
                            p.getViewCount(),
                            p.isNew(),
                            isHot,
                            p.getCreatedAt(),
                            imageCountMap.getOrDefault(pid, 0));
                })
                .toList();

        return new LocationMapPostPageResponse(content, hasNext, nextDistance, nextPostId);
    }
}
