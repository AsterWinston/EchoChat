package me.aster.echochat.message.service.impl;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.entity.MessageRead;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.MessageReadMapper;
import me.aster.echochat.message.websocket.WebSocketChannelManager;
import me.aster.echochat.message.websocket.WebSocketPushService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageReadService} verifying read status queries,
 * permission checks, and sender exclusion from read recipient lists.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageReadService")
class MessageReadServiceTest {

    @Mock private MessageMapper messageMapper;
    @Mock private MessageReadMapper messageReadMapper;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private WebSocketChannelManager channelManager;
    @Mock private WebSocketPushService pushService;
    @Mock private GroupFeignClient groupFeignClient;
    @InjectMocks private MessageReadService messageReadService;

    @Nested
    @DisplayName("getGroupReadStatus")
    class GetGroupReadStatus {

        @Test
        @DisplayName("should throw NOT_FOUND when message does not exist")
        void shouldThrowNotFoundWhenMessageNotExist() {
            when(messageMapper.findByMsgId(999L)).thenReturn(null);

            assertThatThrownBy(() -> messageReadService.getGroupReadStatus(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Message not found");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when user is not participant of single chat")
        void shouldThrowForbiddenWhenNotParticipantOfSingleChat() {
            Message msg = new Message();
            msg.setMsgId(100L);
            msg.setSessionType("single");
            msg.setFromUid(10L);
            msg.setToId("20");
            when(messageMapper.findByMsgId(100L)).thenReturn(msg);

            assertThatThrownBy(() -> messageReadService.getGroupReadStatus(100L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission to view read status");
        }

        @Test
        @DisplayName("should throw FORBIDDEN when user is not member of group")
        void shouldThrowForbiddenWhenNotGroupMember() {
            Message msg = new Message();
            msg.setMsgId(200L);
            msg.setSessionType("group");
            msg.setFromUid(10L);
            msg.setToId("50");
            when(messageMapper.findByMsgId(200L)).thenReturn(msg);
            when(groupFeignClient.checkMembership(50L, 30L)).thenReturn(Map.of("member", false));

            assertThatThrownBy(() -> messageReadService.getGroupReadStatus(200L, 30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Not a member of this group");
        }

        @Test
        @DisplayName("should return read status for single chat participant")
        void shouldReturnReadStatusForSingleChatParticipant() {
            Message msg = new Message();
            msg.setMsgId(100L);
            msg.setSessionType("single");
            msg.setFromUid(10L);
            msg.setToId("20");
            when(messageMapper.findByMsgId(100L)).thenReturn(msg);

            MessageRead read = new MessageRead();
            read.setUid(20L);
            read.setMsgId(100L);
            read.setReadAt(LocalDateTime.now());
            when(messageReadMapper.findByMsgId(100L)).thenReturn(List.of(read));

            Map<String, Object> result = messageReadService.getGroupReadStatus(100L, 20L);

            assertThat(result).containsKeys("readCount", "readUids");
            assertThat(result.get("readCount")).isEqualTo(1);
            @SuppressWarnings("unchecked")
            java.util.Set<String> uids = (java.util.Set<String>) result.get("readUids");
            assertThat(uids).contains("20");
        }

        @Test
        @DisplayName("should exclude message sender from readUids")
        void shouldExcludeSenderFromReadUids() {
            Message msg = new Message();
            msg.setMsgId(300L);
            msg.setSessionType("group");
            msg.setFromUid(10L);
            msg.setToId("50");
            when(messageMapper.findByMsgId(300L)).thenReturn(msg);
            when(groupFeignClient.checkMembership(50L, 20L)).thenReturn(Map.of("member", true));

            MessageRead senderRead = new MessageRead();
            senderRead.setUid(10L);
            senderRead.setMsgId(300L);
            MessageRead otherRead = new MessageRead();
            otherRead.setUid(20L);
            otherRead.setMsgId(300L);
            when(messageReadMapper.findByMsgId(300L)).thenReturn(List.of(senderRead, otherRead));

            Map<String, Object> result = messageReadService.getGroupReadStatus(300L, 20L);

            assertThat(result.get("readCount")).isEqualTo(1);
            @SuppressWarnings("unchecked")
            java.util.Set<String> uids = (java.util.Set<String>) result.get("readUids");
            assertThat(uids).containsExactly("20");
        }
    }
}
