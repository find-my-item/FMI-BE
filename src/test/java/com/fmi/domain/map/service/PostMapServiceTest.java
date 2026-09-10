package com.fmi.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.map.exception.MapErrorStatus;
import com.fmi.domain.map.web.dto.response.MapPostPageResponse;
import com.fmi.domain.map.web.dto.response.PostMarkerResponse;
import com.fmi.domain.post.data.Post;
import com.fmi.domain.post.data.PostType;
import com.fmi.domain.post.data.Radius;
import com.fmi.domain.post.repository.PostRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.domain.userblock.data.BlockedUser;
import com.fmi.domain.userblock.repository.BlockedUserRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("PostMapService")
class PostMapServiceTest extends IntegrationTestSupport {

    @Autowired
    private PostMapService postMapService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Nested
    @DisplayName("좌표 주변 게시글을 조회할 때")
    class DescribeGetNearbyPosts {

        @Nested
        @DisplayName("양방향 차단 사용자의 게시글이 있으면")
        class ContextWithBlockedAuthors {

            @Test
            @DisplayName("차단 관계가 없는 작성자의 게시글만 목록과 마커에 반환한다")
            void itReturnsOnlyPostsFromVisibleAuthors() {
                // given
                User requester = userRepository.save(User.builder()
                        .nickname("주변조회자")
                        .email("nearby-requester@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                User visibleAuthor = userRepository.save(User.builder()
                        .nickname("노출작성자")
                        .email("visible-author@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                User blockedAuthor = userRepository.save(User.builder()
                        .nickname("차단작성자")
                        .email("blocked-author@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                User blockingAuthor = userRepository.save(User.builder()
                        .nickname("역차단작성자")
                        .email("blocking-author@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                Post visiblePost = postRepository.save(Post.create(
                        "노출 게시글",
                        "서울 성동구",
                        37.5422,
                        127.0549,
                        PostType.LOST,
                        Category.ELECTRONICS,
                        "노출할 게시글입니다.",
                        false,
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        Radius.DISTANCE_1000,
                        visibleAuthor));
                Post deletedPost = postRepository.save(Post.create(
                        "삭제 게시글",
                        "서울 성동구",
                        37.5422,
                        127.0549,
                        PostType.LOST,
                        Category.ELECTRONICS,
                        "삭제된 게시글입니다.",
                        false,
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        Radius.DISTANCE_1000,
                        visibleAuthor));
                deletedPost.softDelete();
                postRepository.save(Post.create(
                        "임시 게시글",
                        "서울 성동구",
                        37.5422,
                        127.0549,
                        PostType.LOST,
                        Category.ELECTRONICS,
                        "임시 저장 게시글입니다.",
                        true,
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        Radius.DISTANCE_1000,
                        visibleAuthor));
                postRepository.save(Post.create(
                        "차단 게시글",
                        "서울 성동구",
                        37.5422,
                        127.0549,
                        PostType.LOST,
                        Category.ELECTRONICS,
                        "차단한 사용자의 게시글입니다.",
                        false,
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        Radius.DISTANCE_1000,
                        blockedAuthor));
                postRepository.save(Post.create(
                        "역차단 게시글",
                        "서울 성동구",
                        37.5422,
                        127.0549,
                        PostType.LOST,
                        Category.ELECTRONICS,
                        "나를 차단한 사용자의 게시글입니다.",
                        false,
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        Radius.DISTANCE_1000,
                        blockingAuthor));
                blockedUserRepository.save(BlockedUser.builder()
                        .blocker(requester)
                        .blocked(blockedAuthor)
                        .build());
                blockedUserRepository.save(BlockedUser.builder()
                        .blocker(blockingAuthor)
                        .blocked(requester)
                        .build());
                UserDetails userDetails = mock(UserDetails.class);
                when(userDetails.getUsername()).thenReturn(requester.getEmail());

                // when
                MapPostPageResponse posts =
                        postMapService.getNearbyPosts(37.5421, 127.0549, 1, null, null, null, null, null, userDetails);
                List<PostMarkerResponse> markers =
                        postMapService.getNearbyPostMarkers(37.5421, 127.0549, 1, null, null, null, userDetails);

                // then
                assertThat(posts.posts()).extracting(post -> post.id()).containsExactly(visiblePost.getId());
                assertThat(markers).extracting(PostMarkerResponse::postId).containsExactly(visiblePost.getId());
            }
        }

        @Nested
        @DisplayName("거리와 게시글 ID 중 하나의 커서만 전달하면")
        class ContextWithIncompleteCursor {

            @Test
            @DisplayName("MAP400-CURSOR_INVALID를 반환한다")
            void itReturnsMapCursorInvalid() {
                // when & then
                assertThatThrownBy(() ->
                                postMapService.getNearbyPosts(37.5421, 127.0549, 1, null, null, null, 10.0, null, null))
                        .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                                .isEqualTo(MapErrorStatus.CURSOR_INVALID));
            }
        }
    }
}
