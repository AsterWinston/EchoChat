package me.aster.echochat.message.websocket;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.OfflineMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WebSocketPushService#deliver}的单元测试，验证当用户没有本地通道时，
 * 帧通过路由注册表路由到其他活跃实例。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketPushService deliver")
class WebSocketPushServiceDeliverTest {

    @Mock private WebSocketChannelManager channelManager;
    @Mock private OfflineMessageMapper offlineMessageMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private UserFeignClient userFeignClient;
    @Mock private WsRouteRegistry routeRegistry;
    @InjectMocks private WebSocketPushService pushService;

    @Test
    @DisplayName("should route to remote instance when user has no local channel")
    void shouldRouteToRemoteInstance() {
        when(channelManager.getUserChannels(10L)).thenReturn(List.of());
        when(routeRegistry.liveInstances(10L)).thenReturn(Set.of("remote-node"));
        when(routeRegistry.getInstanceId()).thenReturn("self-node");

        boolean delivered = pushService.deliver(10L, "{\"type\":\"message\"}");

        assertThat(delivered).isTrue();
        verify(routeRegistry).publish(eq("remote-node"), contains("\"uid\":10"));
    }

    @Test
    @DisplayName("should not publish to itself and report undelivered when only route is self")
    void shouldSkipSelfRoute() {
        when(channelManager.getUserChannels(10L)).thenReturn(List.of());
        when(routeRegistry.liveInstances(10L)).thenReturn(Set.of("self-node"));
        when(routeRegistry.getInstanceId()).thenReturn("self-node");

        boolean delivered = pushService.deliver(10L, "{\"type\":\"message\"}");

        assertThat(delivered).isFalse();
        verify(routeRegistry, never()).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("should report undelivered when there is no route at all")
    void shouldReportUndeliveredWithoutRoutes() {
        when(channelManager.getUserChannels(10L)).thenReturn(List.of());
        when(routeRegistry.liveInstances(10L)).thenReturn(Set.of());

        boolean delivered = pushService.deliver(10L, "{\"type\":\"message\"}");

        assertThat(delivered).isFalse();
        verify(routeRegistry, never()).publish(anyString(), anyString());
    }
}
