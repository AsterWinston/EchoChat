package me.aster.echochat.message.config;

import me.aster.echochat.message.websocket.WsPushSubscriber;
import me.aster.echochat.message.websocket.WsRouteRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 配置Redis Pub/Sub监听器容器，在本实例的专用通道
 * （{@code ws:push:{instanceId}}）上接收跨节点WebSocket帧。
 * @author AsterWinston
 */
@Configuration
public class WsRoutingConfig {

    /**
     * @param connectionFactory Redis连接工厂
     * @param subscriber        路由帧处理器
     * @param routeRegistry     提供本实例通道的路由注册表
     * @return 订阅了本实例推送通道的监听器容器
     */
    @Bean
    public RedisMessageListenerContainer wsPushListenerContainer(RedisConnectionFactory connectionFactory,
                                                                 WsPushSubscriber subscriber,
                                                                 WsRouteRegistry routeRegistry) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(routeRegistry.selfChannel()));
        return container;
    }
}