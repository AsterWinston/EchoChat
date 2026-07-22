package me.aster.echochat.moment.service.impl;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.moment.client.UserFeignClient;
import me.aster.echochat.moment.entity.*;
import me.aster.echochat.moment.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MomentServiceImpl")
class MomentServiceImplTest {

    @Mock private MomentMapper momentMapper;
    @Mock private MomentLikeMapper momentLikeMapper;
    @Mock private MomentCommentMapper momentCommentMapper;
    @Mock private MomentPrivacyMapper momentPrivacyMapper;
    @Mock private FeedTimelineMapper feedTimelineMapper;
    @Mock private UserFeignClient userFeignClient;
    @Mock private SnowflakeIdGenerator idGenerator;
    @InjectMocks private MomentServiceImpl momentService;

    private Moment createMoment(Long momentId, Long uid) {
        Moment m = new Moment();
        m.setMomentId(momentId);
        m.setUid(uid);
        m.setContent("Hello world");
        m.setMedia(null);
        m.setVisibility("public");
        m.setShowRange(null);
        m.setCreatedAt(LocalDateTime.now().minusHours(1));
        m.setUpdatedAt(LocalDateTime.now().minusHours(1));
        m.setIsDeleted(0);
        return m;
    }

    private MomentLike createMomentLike(Long id, Long momentId, Long uid) {
        MomentLike like = new MomentLike();
        like.setId(id);
        like.setMomentId(momentId);
        like.setUid(uid);
        like.setCreatedAt(LocalDateTime.now());
        return like;
    }

    private FeedTimeline createFeed(Long ownerUid, Long momentId, Long authorUid) {
        FeedTimeline f = new FeedTimeline();
        f.setOwnerUid(ownerUid);
        f.setMomentId(momentId);
        f.setAuthorUid(authorUid);
        f.setCreatedAt(LocalDateTime.now().minusHours(1));
        return f;
    }

    @Nested
    @DisplayName("publish")
    class PublishTests {

        @Test
        @DisplayName("should publish a public moment and distribute to friends")
        void shouldPublishPublicMoment() {
            when(idGenerator.nextId()).thenReturn(1000L);
            when(userFeignClient.getFriendUids(100L)).thenReturn(List.of(200L, 300L));
            when(momentMapper.insert(any(Moment.class))).thenReturn(1);
            when(feedTimelineMapper.insert(any(FeedTimeline.class))).thenReturn(1);

            Moment result = momentService.publish(100L, "Hello", null, null, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getMomentId()).isEqualTo(1000L);
            assertThat(result.getUid()).isEqualTo(100L);
            assertThat(result.getContent()).isEqualTo("Hello");
            assertThat(result.getVisibility()).isEqualTo("public");
            assertThat(result.getIsDeleted()).isEqualTo(0);

            verify(feedTimelineMapper, times(3)).insert(any(FeedTimeline.class));
            verify(momentPrivacyMapper, never()).insert(any(MomentPrivacy.class));
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when content and media both empty")
        void shouldThrowWhenContentAndMediaBothEmpty() {
            assertThatThrownBy(() -> momentService.publish(100L, null, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Content and media cannot both be empty");

            assertThatThrownBy(() -> momentService.publish(100L, "  ", "", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Content and media cannot both be empty");
        }

        @Test
        @DisplayName("should insert privacy records for restricted visibility")
        void shouldInsertPrivacyRecordsForRestricted() {
            when(idGenerator.nextId()).thenReturn(2000L);
            when(userFeignClient.getFriendUids(100L)).thenReturn(List.of(200L));
            when(momentMapper.insert(any(Moment.class))).thenReturn(1);
            when(feedTimelineMapper.insert(any(FeedTimeline.class))).thenReturn(1);

            Moment result = momentService.publish(100L, "Secret", null, "restricted", List.of(300L, 400L), null);

            assertThat(result.getVisibility()).isEqualTo("restricted");
            verify(momentPrivacyMapper, times(2)).insert(any(MomentPrivacy.class));
        }
    }

    @Nested
    @DisplayName("deleteMoment")
    class DeleteMomentTests {

        @Test
        @DisplayName("should soft-delete moment and cascade-remove associations")
        void shouldDeleteMomentAndCascade() {
            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            momentService.deleteMoment(100L, 1000L);

            assertThat(moment.getIsDeleted()).isEqualTo(1);
            verify(momentMapper).updateById(moment);
            verify(momentLikeMapper).deleteByMomentId(1000L);
            verify(momentCommentMapper).deleteByMomentId(1000L);
            verify(feedTimelineMapper).deleteByMomentId(1000L);
            verify(momentPrivacyMapper).deleteByMomentId(1000L);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when moment does not exist")
        void shouldThrowNotFoundWhenMomentMissing() {
            when(momentMapper.findActiveById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.deleteMoment(100L, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Moment not found or deleted");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when user is not the moment owner")
        void shouldThrowForbiddenWhenNotOwner() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            assertThatThrownBy(() -> momentService.deleteMoment(100L, 1000L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Can only delete own moments");
        }
    }

    @Nested
    @DisplayName("getFeed")
    class GetFeedTests {

        @Test
        @DisplayName("should return enriched feed items")
        void shouldReturnFeedItems() {
            FeedTimeline feed1 = createFeed(100L, 1000L, 200L);
            FeedTimeline feed2 = createFeed(100L, 1001L, 300L);
            when(feedTimelineMapper.findByOwner(eq(100L), any(LocalDateTime.class), eq(10)))
                    .thenReturn(List.of(feed1, feed2));

            Moment m1 = createMoment(1000L, 200L);
            Moment m2 = createMoment(1001L, 300L);
            when(momentMapper.selectBatchIds(List.of(1000L, 1001L))).thenReturn(List.of(m1, m2));

            when(momentLikeMapper.findLikedMomentIds(eq(100L), anyList()))
                    .thenReturn(List.of(1000L));
            when(userFeignClient.getBlacklistUids(100L)).thenReturn(List.of());
            when(momentLikeMapper.countByMomentIds(anyList()))
                    .thenReturn(List.of(Map.of("moment_id", 1000L, "cnt", 5), Map.of("moment_id", 1001L, "cnt", 3)));
            when(momentCommentMapper.countByMomentIds(anyList()))
                    .thenReturn(List.of(Map.of("moment_id", 1000L, "cnt", 2), Map.of("moment_id", 1001L, "cnt", 1)));
            when(momentPrivacyMapper.findBlockUidsByMomentIds(anyList()))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = momentService.getFeed(100L, null, 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("momentId", "1000")
                    .containsEntry("likeCount", 5)
                    .containsEntry("commentCount", 2)
                    .containsEntry("isLiked", true);
            assertThat(result.get(1)).containsEntry("momentId", "1001")
                    .containsEntry("likeCount", 3)
                    .containsEntry("commentCount", 1)
                    .containsEntry("isLiked", false);
        }

        @Test
        @DisplayName("should return empty list when no feed entries")
        void shouldReturnEmptyWhenNoFeedEntries() {
            when(feedTimelineMapper.findByOwner(eq(100L), any(LocalDateTime.class), eq(10)))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = momentService.getFeed(100L, null, 10);

            assertThat(result).isEmpty();
            verify(momentMapper, never()).selectBatchIds(anyList());
        }
    }

    @Nested
    @DisplayName("getUserMoments")
    class GetUserMomentsTests {

        @Test
        @DisplayName("should return user moments with enrichment data")
        void shouldReturnUserMoments() {
            when(userFeignClient.getBlacklistUids(100L)).thenReturn(List.of());
            when(userFeignClient.getBlacklistUids(200L)).thenReturn(List.of());

            Moment m1 = createMoment(1000L, 200L);
            Moment m2 = createMoment(1001L, 200L);
            when(momentMapper.findByUidPaginated(eq(200L), any(LocalDateTime.class), eq(10)))
                    .thenReturn(List.of(m1, m2));

            when(momentLikeMapper.findLikedMomentIds(eq(100L), anyList()))
                    .thenReturn(List.of(1000L));
            when(momentLikeMapper.countByMomentIds(anyList()))
                    .thenReturn(List.of(Map.of("moment_id", 1000L, "cnt", 5), Map.of("moment_id", 1001L, "cnt", 1)));
            when(momentCommentMapper.countByMomentIds(anyList()))
                    .thenReturn(List.of(Map.of("moment_id", 1000L, "cnt", 2), Map.of("moment_id", 1001L, "cnt", 3)));

            List<Moment> result = momentService.getUserMoments(100L, 200L, null, 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getIsLiked()).isTrue();
            assertThat(result.get(0).getLikeCount()).isEqualTo(5);
            assertThat(result.get(0).getCommentCount()).isEqualTo(2);
            assertThat(result.get(1).getIsLiked()).isFalse();
            assertThat(result.get(1).getLikeCount()).isEqualTo(1);
            assertThat(result.get(1).getCommentCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return empty when viewer or target is blocked")
        void shouldReturnEmptyWhenBlocked() {
            when(userFeignClient.getBlacklistUids(100L)).thenReturn(List.of(200L));

            List<Moment> result = momentService.getUserMoments(100L, 200L, null, 10);

            assertThat(result).isEmpty();
            verify(momentMapper, never()).findByUidPaginated(anyLong(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("likeMoment")
    class LikeMomentTests {

        @Test
        @DisplayName("should successfully like own moment")
        void shouldLikeOwnMoment() {
            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            momentService.likeMoment(100L, 1000L);

            verify(momentLikeMapper).findByMomentAndUid(1000L, 100L);
            verify(momentLikeMapper).insert(any(MomentLike.class));
        }

        @Test
        @DisplayName("should successfully like friend's moment when friends")
        void shouldLikeFriendMoment() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);
            when(userFeignClient.checkFriendship(100L, 200L))
                    .thenReturn(Map.of("friends", true));

            momentService.likeMoment(100L, 1000L);

            verify(momentLikeMapper).findByMomentAndUid(1000L, 100L);
            verify(momentLikeMapper).insert(any(MomentLike.class));
        }

        @Test
        @DisplayName("should throw CONFLICT when already liked")
        void shouldThrowConflictWhenAlreadyLiked() {
            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);
            MomentLike existing = createMomentLike(1L, 1000L, 100L);
            when(momentLikeMapper.findByMomentAndUid(1000L, 100L)).thenReturn(existing);

            assertThatThrownBy(() -> momentService.likeMoment(100L, 1000L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Already liked");

            verify(momentLikeMapper, never()).insert(any(MomentLike.class));
        }

        @Test
        @DisplayName("should throw NOT_FOUND when moment does not exist")
        void shouldThrowNotFoundWhenMomentMissing() {
            when(momentMapper.findActiveById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.likeMoment(100L, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Moment not found or deleted");
        }
    }

    @Nested
    @DisplayName("unlikeMoment")
    class UnlikeMomentTests {

        @Test
        @DisplayName("should successfully unlike a moment")
        void shouldUnlikeMoment() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);
            MomentLike existing = createMomentLike(1L, 1000L, 100L);
            when(momentLikeMapper.findByMomentAndUid(1000L, 100L)).thenReturn(existing);

            momentService.unlikeMoment(100L, 1000L);

            verify(momentLikeMapper).deleteById(1L);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when not liked yet")
        void shouldThrowNotFoundWhenNotLiked() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);
            when(momentLikeMapper.findByMomentAndUid(1000L, 100L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.unlikeMoment(100L, 1000L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Not liked yet");

            verify(momentLikeMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("getLikes")
    class GetLikesTests {

        @Test
        @DisplayName("should return likes list for a moment")
        void shouldReturnLikesList() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);
            MomentLike like1 = createMomentLike(1L, 1000L, 100L);
            MomentLike like2 = createMomentLike(2L, 1000L, 300L);
            when(momentLikeMapper.findByMomentId(1000L)).thenReturn(List.of(like1, like2));

            List<MomentLike> result = momentService.getLikes(100L, 1000L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUid()).isEqualTo(100L);
            assertThat(result.get(1).getUid()).isEqualTo(300L);
        }

        @Test
        @DisplayName("should throw NOT_FOUND when moment does not exist")
        void shouldThrowNotFoundForLikes() {
            when(momentMapper.findActiveById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.getLikes(100L, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Moment not found or deleted");
        }
    }

    @Nested
    @DisplayName("comment")
    class CommentTests {

        @Test
        @DisplayName("should successfully add a comment on own moment")
        void shouldCommentOnOwnMoment() {
            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            MomentComment result = momentService.comment(100L, 1000L, null, "Great post!");

            assertThat(result).isNotNull();
            assertThat(result.getMomentId()).isEqualTo(1000L);
            assertThat(result.getUid()).isEqualTo(100L);
            assertThat(result.getContent()).isEqualTo("Great post!");
            verify(momentCommentMapper).insert(any(MomentComment.class));
        }

        @Test
        @DisplayName("should throw BAD_REQUEST when comment content is empty")
        void shouldThrowWhenCommentContentEmpty() {
            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            assertThatThrownBy(() -> momentService.comment(100L, 1000L, null, ""))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Comment content cannot be empty");

            assertThatThrownBy(() -> momentService.comment(100L, 1000L, null, "   "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Comment content cannot be empty");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when moment does not exist")
        void shouldThrowNotFoundForComment() {
            when(momentMapper.findActiveById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.comment(100L, 9999L, null, "Hi"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Moment not found or deleted");
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteCommentTests {

        @Test
        @DisplayName("should delete comment when user is the comment author")
        void shouldDeleteOwnComment() {
            MomentComment comment = new MomentComment();
            comment.setId(1L);
            comment.setMomentId(1000L);
            comment.setUid(100L);
            comment.setContent("Nice!");
            when(momentCommentMapper.selectById(1L)).thenReturn(comment);

            momentService.deleteComment(100L, 1L);

            verify(momentCommentMapper).deleteById(1L);
        }

        @Test
        @DisplayName("should delete comment when user is the moment author but not comment author")
        void shouldDeleteCommentAsMomentOwner() {
            MomentComment comment = new MomentComment();
            comment.setId(1L);
            comment.setMomentId(1000L);
            comment.setUid(200L);
            comment.setContent("Nice!");
            when(momentCommentMapper.selectById(1L)).thenReturn(comment);

            Moment moment = createMoment(1000L, 100L);
            when(momentMapper.selectById(1000L)).thenReturn(moment);

            momentService.deleteComment(100L, 1L);

            verify(momentCommentMapper).deleteById(1L);
        }

        @Test
        @DisplayName("should throw FORBIDDEN when user is neither comment nor moment author")
        void shouldThrowForbiddenWhenNotAuthorized() {
            MomentComment comment = new MomentComment();
            comment.setId(1L);
            comment.setMomentId(1000L);
            comment.setUid(200L);
            comment.setContent("Nice!");
            when(momentCommentMapper.selectById(1L)).thenReturn(comment);

            Moment moment = createMoment(1000L, 300L);
            when(momentMapper.selectById(1000L)).thenReturn(moment);

            assertThatThrownBy(() -> momentService.deleteComment(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Can only delete own comments");

            verify(momentCommentMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("should throw NOT_FOUND when comment does not exist")
        void shouldThrowNotFoundWhenCommentMissing() {
            when(momentCommentMapper.selectById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.deleteComment(100L, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Comment not found");
        }
    }

    @Nested
    @DisplayName("getComments")
    class GetCommentsTests {

        @Test
        @DisplayName("should return comments list for a moment")
        void shouldReturnCommentsList() {
            Moment moment = createMoment(1000L, 200L);
            when(momentMapper.findActiveById(1000L)).thenReturn(moment);

            MomentComment c1 = new MomentComment();
            c1.setId(1L);
            c1.setMomentId(1000L);
            c1.setUid(100L);
            c1.setContent("First!");
            c1.setCreatedAt(LocalDateTime.now());
            MomentComment c2 = new MomentComment();
            c2.setId(2L);
            c2.setMomentId(1000L);
            c2.setUid(300L);
            c2.setContent("Second!");
            c2.setCreatedAt(LocalDateTime.now());
            when(momentCommentMapper.findByMomentId(1000L)).thenReturn(List.of(c1, c2));

            List<MomentComment> result = momentService.getComments(100L, 1000L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("First!");
            assertThat(result.get(1).getContent()).isEqualTo("Second!");
        }

        @Test
        @DisplayName("should throw NOT_FOUND when moment does not exist")
        void shouldThrowNotFoundForComments() {
            when(momentMapper.findActiveById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> momentService.getComments(100L, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Moment not found or deleted");
        }
    }
}
