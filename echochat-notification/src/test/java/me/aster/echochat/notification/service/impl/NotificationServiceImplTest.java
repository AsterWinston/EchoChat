package me.aster.echochat.notification.service.impl;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.notification.client.MessageFeignClient;
import me.aster.echochat.notification.entity.Notification;
import me.aster.echochat.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private MessageFeignClient messageFeignClient;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("createNotification")
    class CreateNotificationTests {
        @BeforeEach
        void setUp() {
            when(idGenerator.nextId()).thenReturn(1001L);
            when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        }

        @Test
        @DisplayName("should create notification with all fields and push via WebSocket")
        void shouldCreateNotificationAndPush() {
            when(messageFeignClient.pushNotification(anyLong(), anyMap())).thenReturn(Map.of());

            Notification result = notificationService.createNotification(
                    10L, "like", "New like", "Alice liked your post", 500L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1001L);
            assertThat(result.getUid()).isEqualTo(10L);
            assertThat(result.getType()).isEqualTo("like");
            assertThat(result.getTitle()).isEqualTo("New like");
            assertThat(result.getContent()).isEqualTo("Alice liked your post");
            assertThat(result.getRelatedId()).isEqualTo(500L);
            assertThat(result.getIsRead()).isEqualTo(0);
            assertThat(result.getCreatedAt()).isNotNull();
            verify(notificationMapper).insert(any(Notification.class));
            verify(messageFeignClient).pushNotification(eq(10L), anyMap());
        }

        @Test
        @DisplayName("should succeed even when WebSocket push fails")
        void shouldSucceedWhenWebSocketPushFails() {
            when(messageFeignClient.pushNotification(anyLong(), anyMap()))
                    .thenThrow(new RuntimeException("WebSocket connection failed"));

            Notification result = notificationService.createNotification(
                    20L, "comment", "New comment", "Bob commented on your post", null);

            assertThat(result).isNotNull();
            assertThat(result.getUid()).isEqualTo(20L);
            assertThat(result.getType()).isEqualTo("comment");
            verify(notificationMapper).insert(any(Notification.class));
        }

        @Test
        @DisplayName("should allow null relatedId")
        void shouldAllowNullRelatedId() {
            when(messageFeignClient.pushNotification(anyLong(), anyMap())).thenReturn(Map.of());

            Notification result = notificationService.createNotification(
                    30L, "system", "Welcome", "Welcome to EchoChat", null);

            assertThat(result).isNotNull();
            assertThat(result.getRelatedId()).isNull();
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotificationsTests {

        @Test
        @DisplayName("should return notification list for user")
        void shouldReturnNotificationList() {
            Notification n1 = new Notification();
            n1.setId(1L);
            n1.setUid(10L);
            n1.setType("like");
            n1.setTitle("New like");
            n1.setContent("Someone liked your post");
            n1.setIsRead(0);
            n1.setCreatedAt(LocalDateTime.now());

            Notification n2 = new Notification();
            n2.setId(2L);
            n2.setUid(10L);
            n2.setType("comment");
            n2.setTitle("New comment");
            n2.setContent("Someone commented");
            n2.setIsRead(1);
            n2.setCreatedAt(LocalDateTime.now());

            when(notificationMapper.findByUid(10L, 20)).thenReturn(List.of(n1, n2));

            List<Notification> results = notificationService.getNotifications(10L, 20);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getId()).isEqualTo(1L);
            assertThat(results.get(1).getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should return empty list when no notifications")
        void shouldReturnEmptyList() {
            when(notificationMapper.findByUid(99L, 20)).thenReturn(Collections.emptyList());

            List<Notification> results = notificationService.getNotifications(99L, 20);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCountTests {

        @Test
        @DisplayName("should return unread count for user")
        void shouldReturnUnreadCount() {
            when(notificationMapper.countUnread(10L)).thenReturn(5);

            int count = notificationService.getUnreadCount(10L);

            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("should return zero when no unread notifications")
        void shouldReturnZeroForNoUnread() {
            when(notificationMapper.countUnread(10L)).thenReturn(0);

            int count = notificationService.getUnreadCount(10L);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        private Notification unreadNotification;

        @BeforeEach
        void setUp() {
            unreadNotification = new Notification();
            unreadNotification.setId(1L);
            unreadNotification.setUid(10L);
            unreadNotification.setType("like");
            unreadNotification.setIsRead(0);
        }

        @Test
        @DisplayName("should mark notification as read")
        void shouldMarkAsRead() {
            when(notificationMapper.selectById(1L)).thenReturn(unreadNotification);

            notificationService.markAsRead(10L, 1L);

            assertThat(unreadNotification.getIsRead()).isEqualTo(1);
            verify(notificationMapper).updateById(unreadNotification);
        }

        @Test
        @DisplayName("should throw when notification not found")
        void shouldThrowWhenNotificationNotFound() {
            when(notificationMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> notificationService.markAsRead(10L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ResultCode.NOTIFICATION_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("should throw when notification belongs to different user")
        void shouldThrowWhenBelongsToDifferentUser() {
            when(notificationMapper.selectById(1L)).thenReturn(unreadNotification);

            assertThatThrownBy(() -> notificationService.markAsRead(20L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ResultCode.NOTIFICATION_PERMISSION_DENIED.getCode());
        }

        @Test
        @DisplayName("should skip update when already read")
        void shouldSkipWhenAlreadyRead() {
            unreadNotification.setIsRead(1);
            when(notificationMapper.selectById(1L)).thenReturn(unreadNotification);

            notificationService.markAsRead(10L, 1L);

            verify(notificationMapper, never()).updateById(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("markAllRead")
    class MarkAllReadTests {

        @Test
        @DisplayName("should mark all notifications as read for user")
        void shouldMarkAllRead() {
            when(notificationMapper.markAllRead(10L)).thenReturn(3);

            assertThatCode(() -> notificationService.markAllRead(10L))
                    .doesNotThrowAnyException();

            verify(notificationMapper).markAllRead(10L);
        }

        @Test
        @DisplayName("should handle zero unread notifications")
        void shouldHandleZeroUnread() {
            when(notificationMapper.markAllRead(10L)).thenReturn(0);

            assertThatCode(() -> notificationService.markAllRead(10L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("createFromEvent")
    class CreateFromEventTests {

        private me.aster.echochat.common.dto.NotificationEvent buildEvent() {
            return me.aster.echochat.common.dto.NotificationEvent.builder()
                    .eventId(9001L)
                    .uid(10L)
                    .type("friend_request")
                    .title("New Friend Request")
                    .content("Someone wants to be your friend")
                    .relatedId(500L)
                    .build();
        }

        @Test
        @DisplayName("should persist notification with eventId and push via WebSocket")
        void shouldPersistWithEventId() {
            when(idGenerator.nextId()).thenReturn(1001L);
            when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
            when(messageFeignClient.pushNotification(anyLong(), anyMap())).thenReturn(Map.of());

            notificationService.createFromEvent(buildEvent());

            org.mockito.ArgumentCaptor<Notification> captor =
                    org.mockito.ArgumentCaptor.forClass(Notification.class);
            verify(notificationMapper).insert(captor.capture());
            assertThat(captor.getValue().getEventId()).isEqualTo(9001L);
            assertThat(captor.getValue().getUid()).isEqualTo(10L);
            verify(messageFeignClient).pushNotification(eq(10L), anyMap());
        }

        @Test
        @DisplayName("should skip duplicate event without pushing")
        void shouldSkipDuplicateEvent() {
            when(idGenerator.nextId()).thenReturn(1001L);
            when(notificationMapper.insert(any(Notification.class)))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_event_id"));

            assertThatCode(() -> notificationService.createFromEvent(buildEvent()))
                    .doesNotThrowAnyException();

            verify(messageFeignClient, never()).pushNotification(anyLong(), anyMap());
        }
    }
}
