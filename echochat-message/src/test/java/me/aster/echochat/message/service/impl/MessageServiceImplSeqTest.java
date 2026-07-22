package me.aster.echochat.message.service.impl;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.util.SensitiveWordFilter;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.mapper.ConversationMapper;
import me.aster.echochat.message.mapper.MessageDeletionMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.PinnedMessageMapper;
import me.aster.echochat.message.mq.RocketMqProducer;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageServiceImpl}序号分配的单元测试，验证计数器通过单个Lua脚本调用
 * 原子性地播种和递增，而非使用存在竞争条件的hasKey/set/increment序列。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl seq allocation")
class MessageServiceImplSeqTest {

    @Mock private MessageMapper messageMapper;
    @Mock private ConversationMapper conversationMapper;
    @Mock private PinnedMessageMapper pinnedMessageMapper;
    @Mock private UserFeignClient userFeignClient;
    @Mock private GroupFeignClient groupFeignClient;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private WebSocketPushService pushService;
    @Mock private RocketMqProducer rocketMQProducer;
    @Mock private SensitiveWordFilter sensitiveWordFilter;
    @Mock private MessageDeletionMapper messageDeletionMapper;
    @InjectMocks private MessageServiceImpl messageService;

    @Test
    @DisplayName("should allocate seq through a single atomic Lua script call")
    @SuppressWarnings("unchecked")
    void shouldAllocateSeqAtomically() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(42L);

        Long seq = ReflectionTestUtils.invokeMethod(messageService, "atomicIncrementSeq", "seq:single:1:2", 41L);

        assertThat(seq).isEqualTo(42L);
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("seq:single:1:2")), eq("41"), eq("2592000"));
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("should seed with zero when there is no DB fallback")
    @SuppressWarnings("unchecked")
    void shouldSeedZeroWithoutFallback() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        Long seq = ReflectionTestUtils.invokeMethod(messageService, "atomicIncrementSeq", "seq:group:9", (Long) null);

        assertThat(seq).isEqualTo(1L);
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("seq:group:9")), eq("0"), eq("2592000"));
    }

    @Test
    @DisplayName("should fail loudly when Redis returns no seq")
    @SuppressWarnings("unchecked")
    void shouldThrowWhenScriptReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                messageService, "atomicIncrementSeq", "seq:single:1:2", 0L))
                .isInstanceOf(BusinessException.class);
    }
}
