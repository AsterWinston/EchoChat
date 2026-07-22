package me.aster.echochat.user.service.impl;

import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.user.mq.NotificationEventPublisher;
import me.aster.echochat.user.entity.Blacklist;
import me.aster.echochat.user.entity.Friend;
import me.aster.echochat.user.entity.FriendRequest;
import me.aster.echochat.user.mapper.BlacklistMapper;
import me.aster.echochat.user.mapper.FriendMapper;
import me.aster.echochat.user.mapper.FriendRequestMapper;
import me.aster.echochat.user.mapper.UserMapper;
import me.aster.echochat.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FriendServiceImpl} verifying friend request
 * acceptance with atomic status updates and batch user queries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FriendServiceImpl")
class FriendServiceImplTest {

    @Mock private FriendMapper friendMapper;
    @Mock private FriendRequestMapper friendRequestMapper;
    @Mock private BlacklistMapper blacklistMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserService userService;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @InjectMocks private FriendServiceImpl friendService;

    @Nested
    @DisplayName("acceptRequest")
    class AcceptRequest {

        private FriendRequest request;

        @BeforeEach
        void setUp() {
            request = new FriendRequest();
            request.setId(1L);
            request.setFromUid(10L);
            request.setToUid(20L);
            request.setStatus("pending");
            request.setExpireAt(LocalDateTime.now().plusDays(1));
            request.setCreatedAt(LocalDateTime.now());
        }

        @Test
        @DisplayName("should throw NOT_FOUND when request does not exist")
        void shouldThrowNotFoundWhenRequestNotExist() {
            when(friendRequestMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> friendService.acceptRequest(1L, 20L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Friend request not found");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when current user is not the recipient")
        void shouldThrowForbiddenWhenNotRecipient() {
            when(friendRequestMapper.selectById(1L)).thenReturn(request);

            assertThatThrownBy(() -> friendService.acceptRequest(1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission to handle this request");
        }

        @Test
        @DisplayName("should throw CONFLICT when request is already handled")
        void shouldThrowConflictWhenAlreadyHandled() {
            request.setStatus("accepted");
            when(friendRequestMapper.selectById(1L)).thenReturn(request);

            assertThatThrownBy(() -> friendService.acceptRequest(1L, 20L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Request already handled");
        }

        @Test
        @DisplayName("should use atomic update and create friend records on success")
        void shouldUseAtomicUpdateAndCreateFriends() {
            when(friendRequestMapper.selectById(1L)).thenReturn(request);
            when(friendRequestMapper.updateStatus(1L, "accepted", "pending")).thenReturn(1);
            when(idGenerator.nextId()).thenReturn(101L, 102L);
            when(friendMapper.insert(any(Friend.class))).thenReturn(1);

            friendService.acceptRequest(1L, 20L);

            verify(friendRequestMapper).updateStatus(1L, "accepted", "pending");
            verify(friendMapper, times(2)).insert(any(Friend.class));
        }

        @Test
        @DisplayName("should throw CONFLICT when atomic update affects 0 rows (race condition)")
        void shouldThrowConflictWhenAtomicUpdateAffectsZeroRows() {
            when(friendRequestMapper.selectById(1L)).thenReturn(request);
            when(friendRequestMapper.updateStatus(1L, "accepted", "pending")).thenReturn(0);

            assertThatThrownBy(() -> friendService.acceptRequest(1L, 20L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Request already handled");
        }
    }

    @Nested
    @DisplayName("getFriendList")
    class GetFriendList {

        @Test
        @DisplayName("should batch-select users instead of N+1 queries")
        void shouldBatchSelectUsers() {
            Friend f1 = new Friend();
            f1.setFriendUid(20L);
            f1.setGroupName("friends");
            f1.setCreatedAt(LocalDateTime.now());
            when(friendMapper.findByUid(10L)).thenReturn(List.of(f1));

            User u = new User();
            u.setUid(20L);
            u.setNickname("Alice");
            u.setAvatar("avatar.png");
            when(userMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(u));
            when(userService.getOnlineStatuses(List.of(20L))).thenReturn(java.util.Map.of(20L, false));

            List<Map<String, Object>> result = friendService.getFriendList(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("uid", "20");
            assertThat(result.get(0)).containsEntry("nickname", "Alice");
            verify(userMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("should return empty list when no friends")
        void shouldReturnEmptyWhenNoFriends() {
            when(friendMapper.findByUid(10L)).thenReturn(List.of());

            List<Map<String, Object>> result = friendService.getFriendList(10L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("sendRequest")
    class SendRequest {

        @Test
        @DisplayName("should throw when adding self")
        void shouldThrowWhenAddingSelf() {
            assertThatThrownBy(() -> friendService.sendRequest(1L, 1L, "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot add yourself");
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(2L)).thenReturn(null);

            assertThatThrownBy(() -> friendService.sendRequest(1L, 2L, "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should throw when already friends")
        void shouldThrowWhenAlreadyFriends() {
            User target = new User();
            target.setUid(2L);
            when(userMapper.selectById(2L)).thenReturn(target);
            when(friendMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> friendService.sendRequest(1L, 2L, "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Already friends");
        }

        @Test
        @DisplayName("should throw when pending request already exists")
        void shouldThrowWhenPendingRequestExists() {
            User target = new User();
            target.setUid(2L);
            when(userMapper.selectById(2L)).thenReturn(target);
            when(friendMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(blacklistMapper.findByUidAndBlockedUid(1L, 2L)).thenReturn(null);
            when(blacklistMapper.findByUidAndBlockedUid(2L, 1L)).thenReturn(null);
            FriendRequest pending = new FriendRequest();
            when(friendRequestMapper.findPendingRequest(1L, 2L)).thenReturn(pending);

            assertThatThrownBy(() -> friendService.sendRequest(1L, 2L, "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("pending friend request already exists");
        }
    }

    @Nested
    @DisplayName("rejectRequest")
    class RejectRequest {

        @Test
        @DisplayName("should reject a pending request")
        void shouldRejectPendingRequest() {
            FriendRequest request = new FriendRequest();
            request.setId(1L);
            request.setFromUid(10L);
            request.setToUid(20L);
            request.setStatus("pending");
            when(friendRequestMapper.selectById(1L)).thenReturn(request);
            when(friendRequestMapper.updateById(any(FriendRequest.class))).thenReturn(1);

            assertThatCode(() -> friendService.rejectRequest(1L, 20L))
                    .doesNotThrowAnyException();

            assertThat(request.getStatus()).isEqualTo("rejected");
            assertThat(request.getHandledAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw FORBIDDEN when not the recipient")
        void shouldThrowForbiddenWhenNotRecipient() {
            FriendRequest request = new FriendRequest();
            request.setId(1L);
            request.setFromUid(10L);
            request.setToUid(20L);
            request.setStatus("pending");
            when(friendRequestMapper.selectById(1L)).thenReturn(request);

            assertThatThrownBy(() -> friendService.rejectRequest(1L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission");
        }
    }

    @Nested
    @DisplayName("blacklist")
    class BlacklistOperations {

        @Test
        @DisplayName("should throw when blocking self")
        void shouldThrowWhenBlockingSelf() {
            assertThatThrownBy(() -> friendService.addToBlacklist(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot block yourself");
        }

        @Test
        @DisplayName("should throw when user already in blacklist")
        void shouldThrowWhenAlreadyBlocked() {
            User target = new User();
            target.setUid(2L);
            when(userMapper.selectById(2L)).thenReturn(target);
            when(blacklistMapper.findByUidAndBlockedUid(1L, 2L)).thenReturn(new Blacklist());

            assertThatThrownBy(() -> friendService.addToBlacklist(1L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already in blacklist");
        }

        @Test
        @DisplayName("should add user to blacklist")
        void shouldAddToBlacklist() {
            User target = new User();
            target.setUid(2L);
            when(userMapper.selectById(2L)).thenReturn(target);
            when(blacklistMapper.findByUidAndBlockedUid(1L, 2L)).thenReturn(null);
            when(idGenerator.nextId()).thenReturn(500L);
            when(blacklistMapper.insert(any(Blacklist.class))).thenReturn(1);

            assertThatCode(() -> friendService.addToBlacklist(1L, 2L))
                    .doesNotThrowAnyException();

            verify(blacklistMapper).insert(any(Blacklist.class));
        }

        @Test
        @DisplayName("should remove from blacklist")
        void shouldRemoveFromBlacklist() {
            when(blacklistMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            assertThatCode(() -> friendService.removeFromBlacklist(1L, 2L))
                    .doesNotThrowAnyException();

            verify(blacklistMapper).delete(any(LambdaQueryWrapper.class));
        }
    }
}