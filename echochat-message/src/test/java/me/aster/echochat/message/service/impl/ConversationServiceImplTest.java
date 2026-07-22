package me.aster.echochat.message.service.impl;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.entity.Conversation;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.mapper.ConversationMapper;
import me.aster.echochat.message.mapper.MessageDeletionMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.MessageReadMapper;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConversationServiceImpl} verifying conversation
 * retrieval uses batch queries instead of N+1 patterns.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationServiceImpl")
class ConversationServiceImplTest {

    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private UserFeignClient userFeignClient;
    @Mock private GroupFeignClient groupFeignClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private WebSocketPushService pushService;
    @Mock private MessageDeletionMapper messageDeletionMapper;
    @Mock private MessageReadMapper messageReadMapper;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private ConversationServiceImpl conversationService;

    @Nested
    @DisplayName("getConversations")
    class GetConversations {

        @Test
        @DisplayName("should batch-select messages instead of N+1")
        void shouldBatchSelectMessages() {
            Conversation conv = new Conversation();
            conv.setTargetId("20");
            conv.setSessionType("single");
            conv.setLastMsgId(100L);
            conv.setUnreadCount(2);
            when(conversationMapper.findByUid(10L)).thenReturn(List.of(conv));

            Message msg = new Message();
            msg.setMsgId(100L);
            msg.setContent("Hello");
            msg.setCreatedAt(java.time.LocalDateTime.now());
            when(messageMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(msg));
            when(messageDeletionMapper.selectList(any())).thenReturn(List.of());
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.multiGet(anyList())).thenReturn(java.util.Collections.singletonList(null));

            me.aster.echochat.common.entity.User user = new me.aster.echochat.common.entity.User();
            user.setUid(20L);
            user.setNickname("Alice");
            user.setAvatar("avatar.png");
            when(userFeignClient.getUsersByUids(List.of(20L))).thenReturn(List.of(user));
            when(userFeignClient.getFriendMemos(eq(10L), anyList())).thenReturn(java.util.Map.of());
            when(redisTemplate.executePipelined(any(org.springframework.data.redis.core.RedisCallback.class)))
                    .thenReturn(List.of(0L));

            List<?> result = conversationService.getConversations(10L);

            assertThat(result).hasSize(1);
            verify(messageMapper, never()).selectById(anyLong());
            verify(messageMapper).selectBatchIds(anyList());
            verify(userFeignClient, never()).getUserByUid(anyLong());
            verify(userFeignClient).getUsersByUids(anyList());
        }
    }
}
