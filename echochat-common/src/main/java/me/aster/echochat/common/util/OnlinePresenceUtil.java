package me.aster.echochat.common.util;

import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.Set;

/**
 * 用户在线状态的共享工具类，基于每个用户的Redis ZSET实现：
 * {@code user:online:{uid}}，其中member=deviceId，score=最近心跳时间戳（毫秒）。
 * 仅在设备的心跳落在 {@link #ONLINE_WINDOW} 窗口内时才计为在线，
 * 因此断开的连接会自然过期，不会留下过期的"在线"状态。
 * @author AsterWinston
 */
public final class OnlinePresenceUtil {

    /** 每个用户在线状态ZSET的Redis键前缀。 */
    public static final String ONLINE_KEY_PREFIX = RedisKeyConstants.USER_ONLINE_PREFIX;

    /** 设备最近一次心跳在此窗口内则视为在线。 */
    public static final Duration ONLINE_WINDOW = Duration.ofSeconds(90);

    /** 在线状态键本身的TTL；每次心跳时刷新。 */
    private static final Duration KEY_TTL = BusinessConstants.REDIS_WEEK_TTL;

    private OnlinePresenceUtil() {
    }

    /**
     * @param uid 用户ID
     * @return 格式为 {@code user:online:{uid}} 的在线状态键
     */
    public static String key(Long uid) {
        return ONLINE_KEY_PREFIX + uid;
    }

    /**
     * 将设备标记为在线，或刷新其心跳分数。
     *
     * @param redisTemplate Redis客户端
     * @param uid           用户ID
     * @param deviceId      设备标识符
     */
    public static void markOnline(StringRedisTemplate redisTemplate, Long uid, String deviceId) {
        String key = key(uid);
        redisTemplate.opsForZSet().add(key, deviceId, System.currentTimeMillis());
        redisTemplate.expire(key, KEY_TTL);
    }

    /**
     * 从在线状态集合中移除设备。
     *
     * @param redisTemplate Redis客户端
     * @param uid           用户ID
     * @param deviceId      设备标识符
     */
    public static void markOffline(StringRedisTemplate redisTemplate, Long uid, String deviceId) {
        redisTemplate.opsForZSet().remove(key(uid), deviceId);
    }

    /**
     * 检查用户是否至少有一个设备持有新鲜心跳，
     * 同时惰性清除超出窗口范围的条目。
     *
     * @param redisTemplate Redis客户端
     * @param uid           用户ID
     * @return 用户是否在线
     */
    public static boolean isOnline(StringRedisTemplate redisTemplate, Long uid) {
        String key = key(uid);
        long cutoff = windowCutoff();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        Long count = redisTemplate.opsForZSet().count(key, cutoff, Double.MAX_VALUE);
        return count != null && count > 0;
    }

    /**
     * 返回心跳落在在线窗口内的设备ID集合。
     *
     * @param redisTemplate Redis客户端
     * @param uid           用户ID
     * @return 在线设备ID集合（永远不会返回null）
     */
    public static Set<String> onlineDevices(StringRedisTemplate redisTemplate, Long uid) {
        Set<String> devices = redisTemplate.opsForZSet()
                .rangeByScore(key(uid), windowCutoff(), Double.MAX_VALUE);
        return devices != null ? devices : Set.of();
    }

    /**
     * @return 仍被视为在线的最小心跳时间戳
     */
    public static long windowCutoff() {
        return System.currentTimeMillis() - ONLINE_WINDOW.toMillis();
    }
}