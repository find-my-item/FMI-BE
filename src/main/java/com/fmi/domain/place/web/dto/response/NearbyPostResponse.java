package com.fmi.domain.place.web.dto.response;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.map.web.dto.response.MapPostPageResponse;
import com.fmi.domain.map.web.dto.response.MapPostResponse;
import com.fmi.domain.post.data.PostStatus;
import com.fmi.domain.post.data.PostType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record NearbyPostResponse(List<NearbyPostSummary> posts, boolean hasNext, Double nextDistance, Long nextPostId) {

    public static NearbyPostResponse from(MapPostPageResponse response) {
        return new NearbyPostResponse(
                response.posts().stream().map(NearbyPostSummary::from).toList(),
                response.hasNext(),
                response.nextDistance(),
                response.nextPostId());
    }

    public record NearbyPostSummary(
            Long postId,
            String title,
            String summary,
            String thumbnailImageUrl,
            String address,
            PostStatus postStatus,
            PostType postType,
            Category category,
            Long favoriteCount,
            boolean favoriteStatus,
            Long viewCount,
            OffsetDateTime createdAt,
            Integer imageCount) {

        static NearbyPostSummary from(MapPostResponse post) {
            return new NearbyPostSummary(
                    post.id(),
                    post.title(),
                    post.summary(),
                    post.thumbnailImageUrl(),
                    post.address(),
                    post.postStatus(),
                    post.postType(),
                    post.category(),
                    post.favoriteCount(),
                    post.favoriteStatus(),
                    post.viewCount(),
                    post.createdAt().atOffset(ZoneOffset.ofHours(9)),
                    post.imageCount());
        }
    }
}
