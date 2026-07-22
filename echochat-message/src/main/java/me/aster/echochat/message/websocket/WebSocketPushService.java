package me.aster.echochat.message.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.entity.OfflineMessage;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.OfflineMessageMapper;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.util.OnlinePresenceUtil;
import me.aster.echochat.common.util.RedisLockUtil;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

/**
 * 通过WebSocket向目标用户推送实时消息、已读回执和输入状态指示的服务。
 * 当用户断线时回退到离线存储。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final WebSocketChannelManager channelManager;
    private final OfflineMessageMapper offlineMessageMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final UserFeignClient userFeignClient;
    private final WsRouteRegistry routeRegistry;

    /** 待确认ACK条目的Redis有序集合键：成员="uid:msgId"，分数=下次重试时间戳 */
    private static final String ACK_PENDING_KEY = "ack:pending";
    /** 存储每个待确认ACK条目重试次数的Redis哈希键（字段="uid:msgId"） */
    private static final String ACK_RETRY_COUNT_KEY = "ack:retry";
    /** 分布式锁键，确保同一时间只有一个实例执行ACK重试扫描 */
    private static final String ACK_RETRY_LOCK_KEY = "lock:ack:retry";
    /** 每次重试扫描最多处理的条目数，用于限制Redis/数据库负载 */
    private static final int ACK_RETRY_BATCH_SIZE = 200;
    /** ACK退避重试间隔：5秒、30秒、5分钟 */
    private static final long[] ACK_RETRY_INTERVALS = {5000L, 30000L, 300000L};

    /** 分割"uid:msgId"后的期望段数 */
    private static final int ACK_MEMBER_PARTS = 2;
    private static final int LOCK_LEASE_SECONDS = 4;
    private static final int ACK_RETRY_MAX_RETRIES = 2;
    private static final String STOPPED_MSG = "STOPPED";

    /** 本实例分布式锁获取的唯一所有者令牌 */
    private static final String LOCK_TOKEN = java.util.UUID.randomUUID().toString();

    /** 配置了Java 8时间类型的JSON序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 统一投递网关：先将帧写入本地通道，再通过Redis Pub/Sub路由到
     * 其他持有该用户连接的在线实例（参见 {@link WsRouteRegistry}）。
     * 所有推送路径都经过此方法，确保多实例部署下跨节点投递透明工作。
     *
     * @param uid     目标用户ID
     * @param payload 已序列化的WebSocket文本帧
     * @return 当帧到达本地通道或路由到至少一个在线实例时返回true
     */
    public boolean deliver(Long uid, String payload) {
        boolean delivered = deliverLocal(uid, payload);
        try {
            for (String instance : routeRegistry.liveInstances(uid)) {
                if (instance.equals(routeRegistry.getInstanceId())) {
                    continue;
                }
                String envelope = OBJECT_MAPPER.writeValueAsString(
                        Map.of("uid", uid, "payload", payload));
                routeRegistry.publish(instance, envelope);
                delivered = true;
            }
        } catch (Exception e) {
            log.warn("Cross-node route failed: uid={}, error={}", uid, e.getMessage());
        }
        return delivered;
    }

    /**
     * 将帧写入该用户的所有活跃本地通道。
     *
     * @param uid     目标用户ID
     * @param payload 已序列化的WebSocket文本帧
     * @return 当至少一个本地通道接收了该帧时返回true
     */
    private boolean deliverLocal(Long uid, String payload) {
        Collection<Channel> channels = channelManager.getUserChannels(uid);
        if (channels.isEmpty()) {
            return false;
        }
        TextWebSocketFrame frame = new TextWebSocketFrame(payload);
        boolean pushed = false;
        for (Channel channel : channels) {
            if (channel.isActive()) {
                channel.writeAndFlush(frame.retainedDuplicate());
                pushed = true;
            }
        }
        frame.release();
        return pushed;
    }

    /**
     * @param targetUid 接收者用户ID
     * @param message   要推送的 {@link Message} 消息
     */
    public void pushMessage(Long targetUid, Message message) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(message);
            String payload = WebSocketSession.buildMessage("message", json);
            if (!deliver(targetUid, payload)) {
                storeOffline(targetUid, message);
            }
        } catch (Exception e) {
            log.error("Failed to push message to uid={}", targetUid, e);
            storeOffline(targetUid, message);
        }
    }

    /**
     * 使用 {@code "self_message"} 帧类型将发送者自己的消息通过WebSocket回显给发送者，
     * 使前端能实时显示而不触发通知音或会话列表更新。
     *
     * @param uid     发送者用户ID
     * @param message 要回显的消息
     */
    public void echoToSender(Long uid, Message message) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(message);
            String payload = WebSocketSession.buildSelfMessage(json);
            deliver(uid, payload);
        } catch (Exception e) {
            log.debug("Self-push failed for msgId={}", message.getMsgId());
        }
    }

    /**
     * @param memberUids    所有群成员的用户ID列表
     * @param senderUid     发送者（推送时排除）
     * @param message       要推送的群 {@link Message} 消息
     * @param mentionedUids 被@提醒的用户ID列表，用于强提醒
     */
    public void pushGroupMessage(List<Long> memberUids, Long senderUid, Message message, List<Long> mentionedUids) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(message);
            String normalPayload = WebSocketSession.buildMessage("message", json);
            String mentionedPayload = WebSocketSession.buildMessage("message_mentioned", json);
            for (Long uid : memberUids) {
                if (uid.equals(senderUid)) {
                    continue;
                }
                boolean mentioned = mentionedUids != null && mentionedUids.contains(uid);
                String payload = mentioned ? mentionedPayload : normalPayload;
                if (!deliver(uid, payload)) {
                    // 投递失败的消息进入离线存储
                    storeOffline(uid, message);
                }
            }
        } catch (Exception e) {
            log.error("Failed to push group message to group {}", message.getToId(), e);
        }
    }

    /**
     * 将所有未投递的离线消息推送给用户，并清空离线队列。
     *
     * @param uid     要同步的用户ID
     * @param channel 推送消息的WebSocket通道
     */
    public void syncOfflineMessages(Long uid, Channel channel) {
        List<OfflineMessage> offlineRecords = offlineMessageMapper.findByUid(uid);
        if (offlineRecords.isEmpty()) {
            return;
        }
        try {
            List<Long> msgIds = offlineRecords.stream()
                    .map(OfflineMessage::getMsgId)
                    .distinct()
                    .toList();
            Map<Long, Message> messageMap = messageMapper.selectBatchIds(msgIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Message::getMsgId, m -> m));
            for (OfflineMessage record : offlineRecords) {
                Message message = messageMap.get(record.getMsgId());
                if (message == null) {
                    continue;
                }
                String json = OBJECT_MAPPER.writeValueAsString(message);
                String payload = WebSocketSession.buildMessage("message", json);
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            }
            offlineMessageMapper.deleteByUid(uid);
            log.info("Synced {} offline messages to uid={}", offlineRecords.size(), uid);
        } catch (Exception e) {
            log.error("Failed to sync offline messages to uid={}", uid, e);
        }
    }

    public void syncIncrementalMessages(Long uid, List<Map<String, Object>> sessions, Channel channel) {
        for (Map<String, Object> session : sessions) {
            try {
                String sessionType = (String) session.get("sessionType");
                String targetId = String.valueOf(session.get("targetId"));
                Number afterSeqNum = (Number) session.get("maxSeq");
                long afterSeq = afterSeqNum != null ? afterSeqNum.longValue() : 0L;

                List<Message> messages = messageMapper.findMessagesAfterSeq(sessionType, targetId, afterSeq, 100, uid);
                for (Message message : messages) {
                    String json = OBJECT_MAPPER.writeValueAsString(message);
                    String payload = WebSocketSession.buildMessage("message", json);
                    channel.writeAndFlush(new TextWebSocketFrame(payload));
                }
            } catch (Exception e) {
                log.warn("Failed to sync session {} for uid={}: {}", session, uid, e.getMessage());
            }
        }
        syncOfflineMessages(uid, channel);
    }

    /**
     * 将消息持久化到 offline_message 表以待后续同步。
     * (uid, msg_id) 上的重复记录被忽略，确保ACK重试和首次推送回退不会重复存储同一条消息。
     *
     * @param uid     离线接收者ID
     * @param message 要离线存储的消息
     */
    private void storeOffline(Long uid, Message message) {
        OfflineMessage offline = new OfflineMessage();
        offline.setId(idGenerator.nextId());
        offline.setUid(uid);
        offline.setMsgId(message.getMsgId());
        offline.setSeq(message.getSeq());
        offline.setCreatedAt(LocalDateTime.now());
        offlineMessageMapper.insertIgnore(offline);
    }

    /**
     * 当消息被阅读时，向发送者推送已读回执通知。
     * 瞬态操作：当用户没有任何在线连接时静默丢弃。
     *
     * @param targetUid 要通知的发送者ID
     * @param readerUid 已读消息的用户ID
     * @param seq       已读的最大序列号
     */
    public void pushReadReceipt(Long targetUid, Long readerUid, Long seq) {
        try {
            deliver(targetUid, WebSocketSession.buildReadReceipt(readerUid, seq));
        } catch (Exception e) {
            log.error("Failed to push read receipt to uid={}", targetUid, e);
        }
    }

    /**
     * 向消息发送者推送群聊已读回执，通知其某条消息已被某用户阅读。
     *
     * @param targetUid 要通知的消息发送者ID
     * @param msgId     被阅读的消息ID
     * @param readerUid 阅读者的用户ID
     */
    public void pushGroupReadReceipt(Long targetUid, Long msgId, Long readerUid) {
        try {
            boolean delivered = deliver(targetUid, WebSocketSession.buildGroupReadReceipt(msgId, readerUid));
            log.debug("Group read receipt pushed: targetUid={}, msgId={}, readerUid={}, delivered={}",
                    targetUid, msgId, readerUid, delivered);
        } catch (Exception e) {
            log.error("Failed to push group read receipt to uid={}, msgId={}", targetUid, msgId, e);
        }
    }

    /**
     * 向会话对象发送输入状态指示。
     * 瞬态操作：当用户没有任何在线连接时静默丢弃。
     *
     * @param targetUid 要通知的用户ID
     * @param fromUid   正在输入的用户ID
     */
    public void pushTypingIndicator(Long targetUid, Long fromUid) {
        try {
            deliver(targetUid, WebSocketSession.buildTyping(fromUid));
        } catch (Exception e) {
            log.error("Failed to push typing indicator to uid={}", targetUid, e);
        }
    }

    /**
     * 添加待确认ACK条目以追踪投递确认。有序集合成员为 "uid:msgId"，
     * 客户端ACK可以通过ZREM直接移除；重试次数存储在关联的哈希中。
     *
     * @param uid        接收者用户ID
     * @param msgId      要追踪的消息ID
     * @param retryCount 当前重试级别（0-2），决定下次重试间隔
     */
    private void markPendingAck(Long uid, Long msgId, int retryCount) {
        long nextRetry = System.currentTimeMillis() + ACK_RETRY_INTERVALS[retryCount];
        String member = uid + ":" + msgId;
        redisTemplate.opsForZSet().add(ACK_PENDING_KEY, member, nextRetry);
        redisTemplate.opsForHash().put(ACK_RETRY_COUNT_KEY, member, String.valueOf(retryCount));
        redisTemplate.expire(ACK_PENDING_KEY, BusinessConstants.REDIS_DAY_TTL);
        redisTemplate.expire(ACK_RETRY_COUNT_KEY, BusinessConstants.REDIS_DAY_TTL);
    }

    /**
     * 通过ZREM/HDEL直接调用来移除指定消息ID的待ACK条目——
     * 每条消息O(log N)，而不是全量扫描待确认集合。
     *
     * @param uid    确认收件的用户ID
     * @param msgIds 已确认的消息ID列表
     */
    public void handleAck(Long uid, List<Long> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) {
            return;
        }
        Object[] members = msgIds.stream()
                .map(msgId -> uid + ":" + msgId)
                .toArray();
        redisTemplate.opsForZSet().remove(ACK_PENDING_KEY, members);
        redisTemplate.opsForHash().delete(ACK_RETRY_COUNT_KEY, members);
    }

    /**
     * 定时任务，每5秒执行一次，重试未确认的消息。
     * 使用三级退避策略：5秒、30秒、5分钟。重试耗尽（3次）或用户离线时，
     * 消息存入离线队列。
     * 通过Redis锁保证每次只有一个实例扫描，且每次扫描只处理有限批次。
     */
    @Scheduled(fixedDelay = 5000)
    public void retryUnackedMessages() {
        try {
            if (!RedisLockUtil.tryLock(redisTemplate, ACK_RETRY_LOCK_KEY, LOCK_TOKEN, Duration.ofSeconds(LOCK_LEASE_SECONDS))) {
                return;
            }
            try {
                long now = System.currentTimeMillis();
                Set<String> members = redisTemplate.opsForZSet()
                        .rangeByScore(ACK_PENDING_KEY, 0, now, 0, ACK_RETRY_BATCH_SIZE);
                if (members == null || members.isEmpty()) {
                    return;
                }
                for (String member : members) {
                    retryPendingEntry(member);
                }
            } finally {
                RedisLockUtil.unlock(redisTemplate, ACK_RETRY_LOCK_KEY, LOCK_TOKEN);
            }
        } catch (Exception e) {
            log.warn("ACK retry skipped — Redis unavailable: {}", e.getMessage());
        }
    }

    /**
     * 重新推送单个待确认条目（"uid:msgId"），升级退避级别，
     * 重试耗尽或用户离线时回退到离线存储。
     *
     * @param member 待确认ZSet成员
     */
    private void retryPendingEntry(String member) {
        try {
            String[] parts = member.split(":");
            if (parts.length != ACK_MEMBER_PARTS) {
                removePendingEntry(member);
                return;
            }
            long uid = Long.parseLong(parts[0]);
            long msgId = Long.parseLong(parts[1]);
            Object countValue = redisTemplate.opsForHash().get(ACK_RETRY_COUNT_KEY, member);
            int retryCount = countValue != null ? Integer.parseInt(countValue.toString()) : 0;

            Message message = messageMapper.findByMsgId(msgId);
            if (message == null) {
                removePendingEntry(member);
                return;
            }

            if (retryCount >= ACK_RETRY_MAX_RETRIES) {
                storeOffline(uid, message);
                removePendingEntry(member);
                log.info("ACK retry exhausted for msgId={}, uid={}, stored to offline", msgId, uid);
                return;
            }

            String json = OBJECT_MAPPER.writeValueAsString(message);
            String payload = WebSocketSession.buildMessage("message", json);
            boolean pushed = deliver(uid, payload);

            removePendingEntry(member);
            if (pushed) {
                markPendingAck(uid, msgId, retryCount + 1);
                log.info("ACK retry {}/3 for msgId={}, uid={}", retryCount + 1, msgId, uid);
            } else {
                storeOffline(uid, message);
                log.info("User offline during ACK retry, msgId={}, uid={}", msgId, uid);
            }
        } catch (Exception e) {
            log.error("Failed to retry ACK for member={}", member, e);
        }
    }

    /**
     * 移除待ACK条目及其重试计数器。
     *
     * @param member 待确认ZSet成员（"uid:msgId"）
     */
    private void removePendingEntry(String member) {
        redisTemplate.opsForZSet().remove(ACK_PENDING_KEY, member);
        redisTemplate.opsForHash().delete(ACK_RETRY_COUNT_KEY, member);
    }

    /**
     * WebSocket连接关闭时调用（断连、超时或错误）。
     * 从在线状态ZSET中移除设备，当没有本地连接剩余时从路由表中移除本实例，
     * 并记录最后在线时间戳。
     *
     * @param uid      用户ID
     * @param deviceId 设备标识
     */
    public void handleDisconnect(Long uid, String deviceId) {
        try {
            OnlinePresenceUtil.markOffline(redisTemplate, uid, deviceId);
            if (!channelManager.isOnline(uid)) {
                routeRegistry.unregister(uid);
            }
            redisTemplate.opsForValue().set(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid, String.valueOf(System.currentTimeMillis()), BusinessConstants.REDIS_DEFAULT_TTL);
            try {
                userFeignClient.updateLastSeen(uid);
            } catch (Exception e) {
                log.warn("updateLastSeen failed for uid={}: {}", uid, e.getMessage());
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(STOPPED_MSG)) {
                log.debug("Redis unavailable during shutdown: uid={}", uid);
            } else {
                log.warn("Failed to update offline status in Redis: uid={}", uid, e);
            }
        }
        log.info("User offline recorded: uid={}, deviceId={}", uid, deviceId);
    }

    /**
     * WebSocket连接建立时调用。
     * 在在线ZSET中注册设备心跳，并在用户跨节点路由表中注册本实例。
     *
     * @param uid      用户ID
     * @param deviceId 设备标识
     */
    public void handleConnect(Long uid, String deviceId) {
        try {
            OnlinePresenceUtil.markOnline(redisTemplate, uid, deviceId);
            routeRegistry.register(uid);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains(STOPPED_MSG)) {
                log.debug("Redis unavailable during shutdown: uid={}", uid);
            } else {
                log.warn("Failed to record online status in Redis: uid={}", uid, e);
            }
        }
        log.info("User online recorded: uid={}, deviceId={}", uid, deviceId);
    }

    /**
     * 每次客户端心跳ping时调用；刷新设备的在线状态分数和本实例的路由条目，
     * 确保两者都在存活窗口内。
     *
     * @param uid      用户ID
     * @param deviceId 设备标识
     */
    public void handleHeartbeat(Long uid, String deviceId) {
        try {
            OnlinePresenceUtil.markOnline(redisTemplate, uid, deviceId);
            routeRegistry.register(uid);
        } catch (Exception e) {
            log.debug("Heartbeat presence refresh failed: uid={}, error={}", uid, e.getMessage());
        }
    }

    /**
     * 通过WebSocket向目标用户推送消息撤回通知。
     * 瞬态操作：当用户没有任何在线连接时静默丢弃。
     *
     * @param targetUid 要通知的用户ID
     * @param msgId     被撤回的消息ID
     */
    public void pushRecallEvent(Long targetUid, Long msgId) {
        try {
            deliver(targetUid, WebSocketSession.buildRecall(msgId));
        } catch (Exception e) {
            log.error("Failed to push recall to uid={}", targetUid, e);
        }
    }

    /**
     * 通过WebSocket向目标用户推送通用通知内容。
     * 如果用户在所有节点都离线，通知帧被丢弃
     * （通知本身已由 echochat-notification 持久化存储）。
     *
     * @param uid     目标用户ID
     * @param payload 要推送的通知数据
     */
    public void pushNotification(Long uid, Map<String, Object> payload) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            String frame = "{\"type\":\"notification\",\"data\":" + json + ",\"timestamp\":" + System.currentTimeMillis() + "}";
            boolean delivered = deliver(uid, frame);
            log.debug("Notification push {}: uid={}", delivered ? "delivered" : "dropped (offline)", uid);
        } catch (Exception e) {
            log.warn("Notification push failed: uid={}", uid, e);
        }
    }
}
