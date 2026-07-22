package me.aster.echochat.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.entity.Conversation;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.entity.MessageDeletion;
import me.aster.echochat.message.entity.MessageRead;
import me.aster.echochat.message.mapper.ConversationMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.MessageDeletionMapper;
import me.aster.echochat.message.mapper.MessageReadMapper;
import me.aster.echochat.common.util.OnlinePresenceUtil;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.service.ConversationService;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * {@link ConversationService}的实现，提供带对端用户信息的会话列表、
 * 标记已读、置顶/取消置顶和删除功能。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserFeignClient userFeignClient;
    private final GroupFeignClient groupFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketPushService pushService;
    private final MessageDeletionMapper messageDeletionMapper;
    private final MessageReadMapper messageReadMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * @param uid 用户
     * @return 包含最后消息、对端昵称和头像的富化会话列表
     */
    @Override
    public List<Map<String, Object>> getConversations(Long uid) {
        List<Conversation> conversations = conversationMapper.findByUid(uid);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<Long> msgIds = conversations.stream()
                .map(Conversation::getLastMsgId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Message> msgMap = msgIds.isEmpty() ? Map.of() : messageMapper.selectBatchIds(msgIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Message::getMsgId, m -> m));
        Set<Long> deletedMsgIds = msgIds.isEmpty() ? Set.of() : messageDeletionMapper.selectList(
                new LambdaQueryWrapper<MessageDeletion>()
                        .in(MessageDeletion::getMsgId, msgIds)
                        .eq(MessageDeletion::getUid, uid))
                .stream().map(MessageDeletion::getMsgId).collect(Collectors.toSet());

        List<Long> singleTargetUids = conversations.stream()
                .filter(conv -> BusinessConstants.SESSION_TYPE_SINGLE.equals(conv.getSessionType()))
                .map(conv -> Long.parseLong(conv.getTargetId()))
                .distinct()
                .toList();
        List<Long> groupTargetGids = conversations.stream()
                .filter(conv -> BusinessConstants.SESSION_TYPE_GROUP.equals(conv.getSessionType()))
                .map(conv -> Long.parseLong(conv.getTargetId()))
                .distinct()
                .toList();

        Map<Long, User> userMap = fetchUsers(singleTargetUids);
        Map<String, String> memoMap = fetchMemos(uid, singleTargetUids);
        Map<String, Map<String, Object>> groupInfoMap = fetchGroupInfos(groupTargetGids);
        Map<String, Integer> unreadMap = fetchUnreadCounts(uid, conversations);
        Map<Long, Boolean> onlineMap = fetchOnlineStatuses(singleTargetUids);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Conversation conv : conversations) {
            Map<String, Object> item = new LinkedHashMap<>(16);
            item.put("targetId", conv.getTargetId());
            item.put("sessionType", conv.getSessionType());
            item.put("lastMsgId", conv.getLastMsgId());
            Integer redisUnread = unreadMap.get(unreadKey(uid, conv));
            item.put("unreadCount", redisUnread != null ? redisUnread : conv.getUnreadCount());
            item.put("isPinned", conv.getIsPinned());
            item.put("dnd", conv.getDnd() != null && conv.getDnd() == 1);
            item.put("updatedAt", conv.getUpdatedAt());

            if (conv.getLastMsgId() != null) {
                Message lastMsg = msgMap.get(conv.getLastMsgId());
                if (lastMsg != null) {
                    if (deletedMsgIds.contains(conv.getLastMsgId())) {
                        Message prev = findPrevVisibleMessage(conv, uid, lastMsg);
                        if (prev != null) {
                            item.put("lastMsg", prev.getContent());
                            item.put("lastMsgTime", prev.getCreatedAt());
                        }
                    } else {
                        item.put("lastMsg", lastMsg.getContent());
                        item.put("lastMsgTime", lastMsg.getCreatedAt());
                    }
                }
            }

            String sessionType = conv.getSessionType();
if (BusinessConstants.SESSION_TYPE_SINGLE.equals(sessionType)) {
                Long targetUid = Long.parseLong(conv.getTargetId());
                User targetUser = userMap.get(targetUid);
                if (targetUser != null) {
                    item.put("nickname", targetUser.getNickname());
                    item.put("avatar", targetUser.getAvatar());
                } else {
                    item.put("nickname", conv.getTargetId());
                }
                item.put("online", Boolean.TRUE.equals(onlineMap.get(targetUid)));
                String memo = memoMap.get(conv.getTargetId());
                if (memo != null && !memo.isEmpty()) {
                    item.put("memo", memo);
                }
            } else if (BusinessConstants.SESSION_TYPE_GROUP.equals(sessionType)) {
                Map<String, Object> groupInfo = groupInfoMap.get(conv.getTargetId());
                if (groupInfo != null) {
                    item.put("nickname", groupInfo.getOrDefault("name", BusinessConstants.DEFAULT_GROUP_NAME));
                    item.put("avatar", groupInfo.getOrDefault("avatar", null));
                } else {
                    item.put("nickname", BusinessConstants.DEFAULT_GROUP_NAME);
                }
            }

            result.add(item);
        }
        return result;
    }

    /**
     * 通过单次Feign调用批量加载对端用户资料；
     * 当用户服务不可用时降级为空map。
     */
    private Map<Long, User> fetchUsers(List<Long> uids) {
        if (uids.isEmpty()) {
            return Map.of();
        }
        try {
            return userFeignClient.getUsersByUids(uids).stream()
                    .collect(Collectors.toMap(User::getUid, u -> u, (a, b) -> a));
        } catch (Exception e) {
            log.warn("Batch user fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 通过单次Feign调用批量加载好友备注；失败时降级为无备注。
     */
    private Map<String, String> fetchMemos(Long uid, List<Long> friendUids) {
        if (friendUids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, String> memos = userFeignClient.getFriendMemos(uid, friendUids);
            return memos != null ? memos : Map.of();
        } catch (Exception e) {
            log.warn("Batch memo fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 通过单次Feign调用批量加载群组信息；失败时降级为默认值。
     */
    private Map<String, Map<String, Object>> fetchGroupInfos(List<Long> gids) {
        if (gids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Map<String, Object>> infos = groupFeignClient.getGroupInfoBatch(gids);
            return infos != null ? infos : Map.of();
        } catch (Exception e) {
            log.warn("Batch group info fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 通过单次MGET批量加载Redis未读计数器。
     */
    private Map<String, Integer> fetchUnreadCounts(Long uid, List<Conversation> conversations) {
        List<String> keys = conversations.stream()
                .map(conv -> unreadKey(uid, conv))
                .toList();
        Map<String, Integer> result = new HashMap<>(16);
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                for (int i = 0; i < keys.size(); i++) {
                    String value = values.get(i);
                    if (value != null) {
                        try {
                            result.put(keys.get(i), Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Batch unread fetch failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 通过单次流水线往返批量检查在线状态。
     */
    private Map<Long, Boolean> fetchOnlineStatuses(List<Long> uids) {
        Map<Long, Boolean> result = new HashMap<>(16);
        if (uids.isEmpty()) {
            return result;
        }
        long cutoff = OnlinePresenceUtil.windowCutoff();
        try {
            List<Object> counts = redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        org.springframework.data.redis.connection.StringRedisConnection src =
                                (org.springframework.data.redis.connection.StringRedisConnection) connection;
                        for (Long uid : uids) {
                            src.zCount(OnlinePresenceUtil.key(uid), cutoff, Double.MAX_VALUE);
                        }
                        return null;
                    });
            for (int i = 0; i < uids.size(); i++) {
                Object count = i < counts.size() ? counts.get(i) : null;
                result.put(uids.get(i), count instanceof Number && ((Number) count).longValue() > 0);
            }
        } catch (Exception e) {
            log.warn("Batch online status fetch failed: {}", e.getMessage());
        }
        return result;
    }

    private static String unreadKey(Long uid, Conversation conv) {
        return RedisKeyConstants.UNREAD_PREFIX + ":" + uid + ":" + conv.getSessionType() + ":" + conv.getTargetId();
    }

    /**
     * 将会话标记为已读，即将未读计数重置为零。
     *
     * @param uid         用户ID
     * @param sessionType 会话类型（single或group）
     * @param targetId    对端UID或群组ID（字符串形式）
     */
    @Override
    public void markAsRead(Long uid, String sessionType, String targetId) {
        redisTemplate.opsForValue().set(RedisKeyConstants.UNREAD_PREFIX + ":" + uid + ":" + sessionType + ":" + targetId, "0", BusinessConstants.REDIS_DEFAULT_TTL);

        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(sessionType)) {
            Long maxSeq = messageMapper.findMaxUnreadSeq(Long.parseLong(targetId), String.valueOf(uid));
            int updated = messageMapper.markMessagesAsRead(sessionType, Long.parseLong(targetId), String.valueOf(uid));
            if (updated > 0) {
                pushService.pushReadReceipt(Long.parseLong(targetId), uid, maxSeq);
            }
        } else {
            List<Long> unreadMsgIds = messageMapper.findUnreadGroupMessageIds(targetId, uid);
            if (!unreadMsgIds.isEmpty()) {
                String idxKey = RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + targetId;
                String memberIdx = (String) redisTemplate.opsForHash().get(idxKey, String.valueOf(uid));
                if (memberIdx == null) {
                    Long idx = redisTemplate.opsForValue().increment(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + RedisKeyConstants.SEQ_PREFIX + targetId);
                    redisTemplate.opsForHash().put(idxKey, String.valueOf(uid), String.valueOf(idx));
                    memberIdx = String.valueOf(idx);
                }
                long bitOffset = Long.parseLong(memberIdx);
                for (Long msgId : unreadMsgIds) {
                    redisTemplate.opsForValue().setBit(RedisKeyConstants.READ_BITMAP_PREFIX + msgId, bitOffset, true);
                    redisTemplate.expire(RedisKeyConstants.READ_BITMAP_PREFIX + msgId, BusinessConstants.REDIS_DEFAULT_TTL);
                    messageReadMapper.insertIgnore(readRecord(msgId, uid));
                }
                for (Message msg : messageMapper.selectBatchIds(unreadMsgIds)) {
                    if (msg != null && !msg.getFromUid().equals(uid)) {
                        pushService.pushGroupReadReceipt(msg.getFromUid(), msg.getMsgId(), uid);
                    }
                }
            }
        }
    }

    /**
     * 为指定用户置顶或取消置顶会话。
     *
     * @param uid         用户ID
     * @param sessionType 会话类型（single或group）
     * @param targetId    对端UID或群组ID（字符串形式）
     * @param pinned      true=置顶，false=取消置顶
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinConversation(Long uid, String sessionType, String targetId, boolean pinned) {
        LambdaUpdateWrapper<Conversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Conversation::getUid, uid)
               .eq(Conversation::getSessionType, sessionType)
               .eq(Conversation::getTargetId, targetId)
               .set(Conversation::getIsPinned, pinned ? 1 : 0);
        conversationMapper.update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDnd(Long uid, String sessionType, String targetId, boolean dnd) {
        LambdaUpdateWrapper<Conversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Conversation::getUid, uid)
               .eq(Conversation::getSessionType, sessionType)
               .eq(Conversation::getTargetId, targetId)
               .set(Conversation::getDnd, dnd ? 1 : 0);
        conversationMapper.update(wrapper);
    }

    /**
     * 为指定用户删除会话记录。
     *
     * @param uid         用户ID
     * @param sessionType 会话类型（single或group）
     * @param targetId    对端UID或群组ID（字符串形式）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long uid, String sessionType, String targetId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUid, uid)
               .eq(Conversation::getSessionType, sessionType)
               .eq(Conversation::getTargetId, targetId);
        conversationMapper.delete(wrapper);
    }

    private Message findPrevVisibleMessage(Conversation conv, Long uid, Message lastMsg) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("session_type", conv.getSessionType());
        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(conv.getSessionType())) {
            String targetId = conv.getTargetId();
            String uidStr = String.valueOf(uid);
            wrapper.and(w -> w
                .and(w2 -> w2.eq("from_uid", uid).eq("to_id", targetId))
                .or(w2 -> w2.eq("from_uid", Long.parseLong(targetId)).eq("to_id", uidStr)));
        } else {
            wrapper.eq("to_id", conv.getTargetId());
        }
        wrapper.lt("seq", lastMsg.getSeq());
        wrapper.eq("is_recalled", 0);
        wrapper.notExists("SELECT 1 FROM message_deletion md WHERE md.msg_id = message.msg_id AND md.uid = {0}", uid);
        wrapper.orderByDesc("seq");
        wrapper.last("LIMIT 1");
        return messageMapper.selectOne(wrapper);
    }

    private MessageRead readRecord(Long msgId, Long uid) {
        MessageRead r = new MessageRead();
        r.setId(idGenerator.nextId());
        r.setMsgId(msgId);
        r.setUid(uid);
        r.setReadAt(LocalDateTime.now());
        return r;
    }
}