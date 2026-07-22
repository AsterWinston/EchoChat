package me.aster.echochat.common.util;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.time.Duration;
import java.util.Collections;

/**
 * 轻量级Redis分布式锁工具类，用于在多个服务实例间协调定时任务。
 * 获取锁使用 {@code SET key value NX PX <millis>} 命令（基于{@link java.time.Duration}精度），
 * 释放锁使用Lua脚本的compare-and-delete方式，仅允许持有者释放自己的锁。
 * @author AsterWinston
 */
@Slf4j
public final class RedisLockUtil {

    /** Lua脚本：仅当存储的令牌与调用方令牌匹配时才删除锁键。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "end " +
            "return 0",
            Long.class);

    private RedisLockUtil() {
    }

    /**
     * 尝试获取分布式锁。
     *
     * @param redisTemplate Redis客户端
     * @param lockKey       锁的键名
     * @param token         唯一持有者令牌（如 实例ID + 线程ID）
     * @param ttl           锁过期时间，应超过预期任务执行时长
     * @return 是否成功获取锁
     */
    public static boolean tryLock(StringRedisTemplate redisTemplate, String lockKey, String token, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl));
    }

    /**
     * 释放分布式锁，仅当锁仍由给定令牌持有时才执行释放。
     * 失败被吞掉：过期的锁会通过TTL自动释放。
     *
     * @param redisTemplate Redis客户端
     * @param lockKey       锁的键名
     * @param token         获取锁时使用的持有者令牌
     */
    public static void unlock(StringRedisTemplate redisTemplate, String lockKey, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("Unlock failed for key={}", lockKey, e);
        }
    }
}