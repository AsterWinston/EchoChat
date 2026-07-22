package me.aster.echochat.message.websocket;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 跨节点WebSocket路由表。每个消息服务实例有一个唯一的{@code instanceId}；
 * 当用户连接时，实例在Redis ZSET {@code ws:route:{uid}}中注册自己
 * （成员=instanceId，分数=最后心跳时间）。向其他节点的用户推送消息时，
 * 将帧发布到该节点专用的Redis Pub/Sub通道{@code ws:push:{instanceId}}。
 * 崩溃的实例会在心跳超出存活窗口后自动从路由表中消失。
 * @author AsterWinston
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsRouteRegistry {

    private final StringRedisTemplate redisTemplate;

    /** 本服务实例的唯一标识符。 */
    @Getter
    private final String instanceId = UUID.randomUUID().toString().replace("-", "");

    /** 每个用户路由ZSET的Redis键前缀。 */
    private static final String ROUTE_KEY_PREFIX = "ws:route:";
    /** 每个实例推送通道的Redis Pub/Sub通道前缀。 */
    public static final String PUSH_CHANNEL_PREFIX = "ws:push:";
    /** 路由条目在此窗口内刷新则认为存活。 */
    private static final Duration LIVENESS_WINDOW = Duration.ofSeconds(90);
    /** 路由键的TTL；每次心跳时刷新。 */
    private static final Duration KEY_TTL = Duration.ofDays(1);

    /**
     * @param uid 用户ID
     * @return 路由键{@code ws:route:{uid}}
     */
    private static String routeKey(Long uid) {
        return ROUTE_KEY_PREFIX + uid;
    }

    /**
     * @return 本实例的推送通道名称
     */
    public String selfChannel() {
        return PUSH_CHANNEL_PREFIX + instanceId;
    }

    /**
     * 注册（或心跳）本实例作为该用户的路由。
     *
     * @param uid 用户ID
     */
    public void register(Long uid) {
        try {
            String key = routeKey(uid);
            redisTemplate.opsForZSet().add(key, instanceId, System.currentTimeMillis());
            redisTemplate.expire(key, KEY_TTL);
        } catch (Exception e) {
            log.warn("WS route register failed: uid={}, error={}", uid, e.getMessage());
        }
    }

    /**
     * 从用户的路由中移除本实例（仅当该用户没有剩余本地连接时调用）。
     *
     * @param uid 用户ID
     */
    public void unregister(Long uid) {
        try {
            redisTemplate.opsForZSet().remove(routeKey(uid), instanceId);
        } catch (Exception e) {
            log.warn("WS route unregister failed: uid={}, error={}", uid, e.getMessage());
        }
    }

    /**
     * 返回当前服务该用户连接的在线实例ID集合，
     * 惰性淘汰心跳超出存活窗口的条目。
     *
     * @param uid 用户ID
     * @return 在线实例ID集合（永不为null）
     */
    public Set<String> liveInstances(Long uid) {
        try {
            String key = routeKey(uid);
            long cutoff = System.currentTimeMillis() - LIVENESS_WINDOW.toMillis();
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
            Set<String> instances = redisTemplate.opsForZSet().rangeByScore(key, cutoff, Double.MAX_VALUE);
            return instances != null ? instances : Set.of();
        } catch (Exception e) {
            log.warn("WS route lookup failed: uid={}, error={}", uid, e.getMessage());
            return Set.of();
        }
    }

    /**
     * 将帧发布到另一个实例的推送通道。
     *
     * @param targetInstanceId 目标实例标识符
     * @param body             序列化后的路由信封
     */
    public void publish(String targetInstanceId, String body) {
        redisTemplate.convertAndSend(PUSH_CHANNEL_PREFIX + targetInstanceId, body);
    }
}