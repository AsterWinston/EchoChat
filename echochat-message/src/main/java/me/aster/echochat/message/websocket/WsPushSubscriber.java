package me.aster.echochat.message.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * 订阅本实例的Redis推送通道（{@code ws:push:{instanceId}}），
 * 并将路由帧转发到目标用户的本地连接通道。
 * 这是{@link WsRouteRegistry}中跨节点WebSocket路由的接收端。
 * @author AsterWinston
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsPushSubscriber implements MessageListener {

    private final WebSocketChannelManager channelManager;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * @param message 路由信封：{@code {"uid": <long>, "payload": <frame text>}}
     * @param pattern 通道模式（未使用）
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode node = OBJECT_MAPPER.readTree(body);
            long uid = node.get("uid").asLong();
            String payload = node.get("payload").asText();

            Collection<Channel> channels = channelManager.getUserChannels(uid);
            if (channels.isEmpty()) {
                log.debug("Routed frame dropped, no local channels: uid={}", uid);
                return;
            }
            TextWebSocketFrame frame = new TextWebSocketFrame(payload);
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(frame.retainedDuplicate());
                }
            }
            frame.release();
        } catch (Exception e) {
            log.warn("Failed to handle routed WS frame: {}", e.getMessage());
        }
    }
}