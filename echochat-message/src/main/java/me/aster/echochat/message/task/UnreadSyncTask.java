package me.aster.echochat.message.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.util.RedisLockUtil;
import me.aster.echochat.message.mapper.ConversationMapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 定期将实时Redis未读计数器（{@code unread:{uid}:{sessionType}:{targetId}}）
 * 刷回{@code conversation.unread_count}快照列。Redis是未读计数的唯一实时数据源；
 * MySQL仅在Redis条目缺失时作为降级方案（如过期清除后）。由分布式锁保护，
 * 确保只有一个实例执行扫描。
 * @author AsterWinston
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadSyncTask {

    private final StringRedisTemplate redisTemplate;
    private final ConversationMapper conversationMapper;

    /** 同步扫描的分布式锁键。 */
    private static final String LOCK_KEY = "lock:unread:sync";
    /** 本实例锁获取的唯一所有者令牌。 */
    private static final String LOCK_TOKEN = UUID.randomUUID().toString();

    /**
     * 每5分钟执行一次，扫描未读计数器并将快照写入MySQL。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void syncUnreadToDb() {
        try {
            if (!RedisLockUtil.tryLock(redisTemplate, LOCK_KEY, LOCK_TOKEN, Duration.ofMinutes(4))) {
                return;
            }
            try {
                int synced = 0;
                ScanOptions options = ScanOptions.scanOptions().match(RedisKeyConstants.UNREAD_PREFIX + ":*").count(500).build();
                try (Cursor<String> cursor = redisTemplate.scan(options)) {
                    while (cursor.hasNext()) {
                        String key = cursor.next();
                        // 键布局：unread:{uid}:{sessionType}:{targetId}
                        String[] parts = key.split(":", 4);
                        if (parts.length != 4) {
                            continue;
                        }
                        String value = redisTemplate.opsForValue().get(key);
                        if (value == null) {
                            continue;
                        }
                        try {
                            conversationMapper.updateUnreadCount(
                                    Long.parseLong(parts[1]), parts[2], parts[3], Integer.parseInt(value));
                            synced++;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (synced > 0) {
                    log.info("Unread snapshot synced to MySQL: {} conversations", synced);
                }
            } finally {
                RedisLockUtil.unlock(redisTemplate, LOCK_KEY, LOCK_TOKEN);
            }
        } catch (Exception e) {
            log.warn("Unread sync sweep skipped: {}", e.getMessage());
        }
    }
}