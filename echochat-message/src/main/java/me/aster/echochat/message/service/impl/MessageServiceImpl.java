package me.aster.echochat.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.common.util.SensitiveWordFilter;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.client.UserFeignClient;
import me.aster.echochat.message.entity.Conversation;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.entity.MessageDeletion;
import me.aster.echochat.message.entity.PinnedMessage;
import me.aster.echochat.message.mapper.ConversationMapper;
import me.aster.echochat.message.mapper.MessageDeletionMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.PinnedMessageMapper;
import me.aster.echochat.message.mq.PendingMessageContext;
import me.aster.echochat.message.mq.RocketMqProducer;
import me.aster.echochat.message.service.ElasticsearchService;
import me.aster.echochat.message.service.MessageService;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * {@link MessageService}的实现，处理消息发送、历史记录查询、
 * 撤回、删除、转发、回复和置顶，支持事务和推送。
 * @author AsterWinston
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final PinnedMessageMapper pinnedMessageMapper;
    private final UserFeignClient userFeignClient;
    private final GroupFeignClient groupFeignClient;
    private final SnowflakeIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketPushService pushService;
    private final RocketMqProducer rocketMqProducer;
    private final ElasticsearchService elasticsearchService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final MessageDeletionMapper messageDeletionMapper;


    public MessageServiceImpl(MessageMapper messageMapper, ConversationMapper conversationMapper,
                              PinnedMessageMapper pinnedMessageMapper, UserFeignClient userFeignClient,
                              GroupFeignClient groupFeignClient, SnowflakeIdGenerator idGenerator,
                              StringRedisTemplate redisTemplate, WebSocketPushService pushService,
                              RocketMqProducer rocketMqProducer, @Lazy ElasticsearchService elasticsearchService,
                              SensitiveWordFilter sensitiveWordFilter, MessageDeletionMapper messageDeletionMapper) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.pinnedMessageMapper = pinnedMessageMapper;
        this.userFeignClient = userFeignClient;
        this.groupFeignClient = groupFeignClient;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
        this.pushService = pushService;
        this.rocketMqProducer = rocketMqProducer;
        this.elasticsearchService = elasticsearchService;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.messageDeletionMapper = messageDeletionMapper;
    }
    private static final String UNREAD_KEY_PREFIX = RedisKeyConstants.UNREAD_PREFIX;

    /** 应用于每个会话序号计数器的TTL；每次分配时刷新。 */
    private static final Duration SEQ_KEY_TTL = BusinessConstants.REDIS_DEFAULT_TTL;

    private static String unreadKey(Long uid, String sessionType, String targetId) {
        return UNREAD_KEY_PREFIX + ":" + uid + ":" + sessionType + ":" + targetId;
    }

    /**
     * 原子序号分配: key不存在时用ARGV[1]（DB最大seq）初始化，
     * 递增并刷新TTL（ARGV[2]，秒）— 单次往返完成，
     * 使并发发送者永远不会观察到部分初始化的计数器。
     */
    private static final DefaultRedisScript<Long> SEQ_INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then " +
            "  redis.call('SET', KEYS[1], ARGV[1]) " +
            "end " +
            "local seq = redis.call('INCR', KEYS[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "return seq",
            Long.class);

    /**
     * @param fromUid 发送者
     * @param toUid   接收者
     * @param msgType 消息内容类型
     * @param content 消息正文
     * @return 持久化后的消息
     * @throws BusinessException 如果给自己发消息或好友关系检查失败
     */
    @Override
    public Message sendMessage(Long fromUid, Long toUid, String msgType, String content) {
        return sendMessage(fromUid, toUid, msgType, content, null);
    }

    @Override
    public Message sendMessage(Long fromUid, Long toUid, String msgType, String content, Long replyToMsgId) {
        if (fromUid.equals(toUid)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Cannot send message to yourself");
        }

        Map<String, Boolean> result = userFeignClient.checkFriendship(fromUid, toUid);
        if (result == null || !Boolean.TRUE.equals(result.get(BusinessConstants.FRIENDSHIP_KEY_FRIENDS))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a friend, cannot send message");
        }

        Map<String, Boolean> blacklistCheck = userFeignClient.checkBlacklist(toUid, fromUid);
        if (blacklistCheck != null && Boolean.TRUE.equals(blacklistCheck.get(BusinessConstants.BLACKLIST_KEY_BLOCKED))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You have been blocked, cannot send message");
        }

        validateMessageContent(msgType, content);
        content = sensitiveWordFilter.filter(content);

        String toId = String.valueOf(toUid);
        String seqKey = buildSeqKey(fromUid, toUid);
        Long seq = atomicIncrementSeq(seqKey,
                messageMapper.getMaxSeq(fromUid, toUid, String.valueOf(fromUid), toId));

        Message message = new Message();
        message.setMsgId(idGenerator.nextId());
        message.setSessionType(BusinessConstants.SESSION_TYPE_SINGLE);
        message.setFromUid(fromUid);
        message.setToId(toId);
        message.setMsgType(msgType);
        message.setContent(content);
        message.setStatus(1);
        message.setSeq(seq);
        message.setCreatedAt(LocalDateTime.now());
        message.setReplyToMsgId(replyToMsgId);

        // 通过RocketMQ事务消息持久化MySQL行；MySQL提交后直接执行ES索引、Redis更新和WebSocket推送。
        rocketMqProducer.publish(PendingMessageContext.builder()
                .message(message)
                .singleToUid(toUid)
                .build());

        indexToEs(message);
        incrementUnreadInRedis(toUid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(fromUid));
        pushService.echoToSender(fromUid, message);
        pushService.pushMessage(toUid, message);

        return message;
    }

    /**
     * 查询单聊的会话历史，按序号分页。
     *
     * @param uid       请求用户
     * @param targetUid 会话对方
     * @param beforeSeq 分页游标（首页传null）
     * @param limit     最大返回数
     * @return 按序号降序排列的历史消息列表
     */
    @Override
    public List<Message> getHistory(Long uid, Long targetUid, Long beforeSeq, int limit) {
        return messageMapper.findHistory(uid, targetUid,
                String.valueOf(uid), String.valueOf(targetUid), beforeSeq, limit, uid);
    }

    /**
     * 撤回2分钟内发送的消息。只有原始发送者可以撤回。
     *
     * @param uid   请求用户（必须是发送者）
     * @param msgId 要撤回的消息ID
     * @return 更新为isRecalled=1的消息
     * @throws BusinessException 如果消息不存在、不是发送者、已撤回或超过2分钟
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message recallMessage(Long uid, Long msgId) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }
        if (!message.getFromUid().equals(uid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Can only recall your own messages");
        }
        if (message.getIsRecalled() != null && message.getIsRecalled() == 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Message already recalled");
        }

        long minutes = Duration.between(message.getCreatedAt(), LocalDateTime.now()).toMinutes();
        if (minutes > BusinessConstants.MESSAGE_RECALL_TIMEOUT_MINUTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Cannot recall after " + BusinessConstants.MESSAGE_RECALL_TIMEOUT_MINUTES + " minutes");
        }

        message.setIsRecalled(1);
        messageMapper.updateById(message);
        elasticsearchService.markRecalled(msgId);

        // 更新双方会话的last_msg_id为前一条未撤回消息
        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(message.getSessionType())) {
            Long senderUid = message.getFromUid();
            Long receiverUid = Long.parseLong(message.getToId());
            updateConversationAfterRecall(senderUid, receiverUid, BusinessConstants.SESSION_TYPE_SINGLE, msgId);
            updateConversationAfterRecall(receiverUid, senderUid, BusinessConstants.SESSION_TYPE_SINGLE, msgId);
            pushService.pushRecallEvent(receiverUid, msgId);
        }

        return message;
    }

    /**
     * 消息撤回后，将会话的last_msg_id更新为前一条未撤回消息，或null如果不存在。
     */
    private void updateConversationAfterRecall(Long uid, Long peerUid, String sessionType, Long recalledMsgId) {
        String peerUidStr = String.valueOf(peerUid);
        Conversation conv = conversationMapper.findByUidAndTarget(uid, sessionType, peerUidStr);
        if (conv == null || conv.getLastMsgId() == null || !conv.getLastMsgId().equals(recalledMsgId)) {
            return;
        }
        Message prev = messageMapper.findPrevNonRecalled(sessionType, uid, peerUid,
                String.valueOf(uid), peerUidStr, recalledMsgId);
        conv.setLastMsgId(prev != null ? prev.getMsgId() : null);
        conv.setUpdatedAt(LocalDateTime.now());

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUid, uid)
               .eq(Conversation::getSessionType, sessionType)
               .eq(Conversation::getTargetId, peerUidStr);
        conversationMapper.update(conv, wrapper);
    }

    /**
     * 删除消息。只有发送者或会话参与者可以删除。
     *
     * @param uid   请求用户
     * @param msgId 要删除的消息ID
     * @throws BusinessException 如果消息不存在或没有权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long uid, Long msgId) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }
        if (!message.getFromUid().equals(uid) && !message.getToId().equals(String.valueOf(uid))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "No permission to delete this message");
        }
        MessageDeletion deletion = new MessageDeletion();
        deletion.setMsgId(msgId);
        deletion.setUid(uid);
        deletion.setCreatedAt(LocalDateTime.now());
        messageDeletionMapper.insert(deletion);
    }

    /**
     * 将已有消息作为带转发元数据的新消息转发给另一个用户。
     *
     * @param fromUid 转发者
     * @param toUid   接收者
     * @param msgId   要转发的原消息ID
     * @return 新创建的转发消息
     * @throws BusinessException 如果原消息不存在或好友关系检查失败
     */
    @Override
    public Message forwardMessage(Long fromUid, Long toUid, Long msgId) {
        Message original = messageMapper.findByMsgId(msgId);
        if (original == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }

        Map<String, Boolean> result = userFeignClient.checkFriendship(fromUid, toUid);
        if (result == null || !Boolean.TRUE.equals(result.get(BusinessConstants.FRIENDSHIP_KEY_FRIENDS))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a friend, cannot forward message");
        }

        Map<String, Boolean> blacklistCheck = userFeignClient.checkBlacklist(toUid, fromUid);
        if (blacklistCheck != null && Boolean.TRUE.equals(blacklistCheck.get(BusinessConstants.BLACKLIST_KEY_BLOCKED))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You have been blocked, cannot forward message");
        }

        String toId = String.valueOf(toUid);
        String seqKey = buildSeqKey(fromUid, toUid);
        Long seq = atomicIncrementSeq(seqKey,
                messageMapper.getMaxSeq(fromUid, toUid, String.valueOf(fromUid), toId));

        Message forwarded = new Message();
        forwarded.setMsgId(idGenerator.nextId());
        forwarded.setSessionType(BusinessConstants.SESSION_TYPE_SINGLE);
        forwarded.setFromUid(fromUid);
        forwarded.setToId(toId);
        forwarded.setMsgType(original.getMsgType());
        String forwardContent = original.getContent();
        if (BusinessConstants.MSG_TYPE_TEXT.equals(original.getMsgType())) {
            forwardContent = sensitiveWordFilter.filter(forwardContent);
        }
        forwarded.setContent(forwardContent);
        forwarded.setIsForwarded(1);
        forwarded.setForwardFromUid(original.getFromUid());
        forwarded.setStatus(1);
        forwarded.setSeq(seq);
        forwarded.setCreatedAt(LocalDateTime.now());

        rocketMqProducer.publish(PendingMessageContext.builder()
                .message(forwarded)
                .singleToUid(toUid)
                .build());

        indexToEs(forwarded);
        incrementUnreadInRedis(toUid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(fromUid));
        pushService.echoToSender(fromUid, forwarded);
        pushService.pushMessage(toUid, forwarded);

        return forwarded;
    }

    @Override
    public Message forwardGroupMessage(Long fromUid, Long gid, Long msgId) {
        Message original = messageMapper.findByMsgId(msgId);
        if (original == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }

        Map<String, Object> membership = groupFeignClient.checkMembership(gid, fromUid);
        if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a group member, cannot forward");
        }

        String toId = String.valueOf(gid);
        String seqKey = RedisKeyConstants.SEQ_PREFIX + RedisKeyConstants.SEQ_GROUP_SUFFIX + gid;
        Long seq = atomicIncrementSeq(seqKey, messageMapper.getMaxGroupSeq(gid));

        Message forwarded = new Message();
        forwarded.setMsgId(idGenerator.nextId());
        forwarded.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
        forwarded.setFromUid(fromUid);
        forwarded.setToId(toId);
        forwarded.setMsgType(original.getMsgType());
        String forwardContent = original.getContent();
        if (BusinessConstants.MSG_TYPE_TEXT.equals(original.getMsgType())) {
            forwardContent = sensitiveWordFilter.filter(forwardContent);
        }
        forwarded.setContent(forwardContent);
        forwarded.setIsForwarded(1);
        forwarded.setForwardFromUid(original.getFromUid());
        forwarded.setStatus(1);
        forwarded.setSeq(seq);
        forwarded.setCreatedAt(LocalDateTime.now());

        Map<String, Integer> countResult = groupFeignClient.getMemberCount(gid);
        int memberCount = countResult != null ? countResult.getOrDefault(BusinessConstants.MEMBER_COUNT_KEY, 0) : 0;
        List<Long> memberUids = groupFeignClient.getMemberUids(gid);
        List<Long> receiverUids = memberUids.stream()
                .filter(memberUid -> !memberUid.equals(fromUid))
                .toList();
        boolean fanOut = memberCount <= BusinessConstants.GROUP_FANOUT_THRESHOLD;

        rocketMqProducer.publish(PendingMessageContext.builder()
                .message(forwarded)
                .groupReceiverUids(receiverUids)
                .groupFanOut(fanOut)
                .build());

        if (fanOut && !receiverUids.isEmpty()) {
            bumpGroupUnreadCounters(receiverUids, toId);
        }
        pushService.echoToSender(fromUid, forwarded);
        pushService.pushGroupMessage(memberUids, fromUid, forwarded, Collections.emptyList());

        return forwarded;
    }

    /**
     * 发送引用已有消息的回复消息。
     *
     * @param fromUid      回复者
     * @param toUid        接收者
     * @param replyToMsgId 被回复的消息ID
     * @param content      回复文本内容
     * @return 设置了replyToMsgId的回复消息
     * @throws BusinessException 如果引用的消息不存在
     */
    @Override
    public Message replyMessage(Long fromUid, Long toUid, Long replyToMsgId, String content) {
        Message replied = messageMapper.findByMsgId(replyToMsgId);
        if (replied == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Referenced message not found");
        }

        return sendMessage(fromUid, toUid, BusinessConstants.MSG_TYPE_TEXT, content, replyToMsgId);
    }

    /**
     * 发送群消息，支持慢速模式、@提及以及
     * 写扩散（≤500成员）与读扩散（>500成员）的切换。
     *
     * @param fromUid 发送者
     * @param gid     目标群组ID
     * @param msgType 消息内容类型
     * @param content 消息正文
     * @return 持久化后的群消息
     * @throws BusinessException 如果不是成员、被禁言、违反慢速模式或@all使用不当
     */
    @Override
    public Message sendGroupMessage(Long fromUid, Long gid, String msgType, String content, Long replyToMsgId) {
        Map<String, Object> membership = groupFeignClient.checkMembership(gid, fromUid);
        if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        if (Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MUTED))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You are muted, cannot send messages");
        }
        if (Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MUTE_ALL))) {
            String role = (String) membership.get(BusinessConstants.MEMBERSHIP_KEY_ROLE);
            if (!BusinessConstants.ROLE_OWNER.equals(role) && !BusinessConstants.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Group is in mute-all mode");
            }
        }

        Integer slowModeInterval = (Integer) membership.get(BusinessConstants.MEMBERSHIP_KEY_SLOW_MODE_INTERVAL);
        if (slowModeInterval != null && slowModeInterval > 0) {
            String slowKey = RedisKeyConstants.SLOWMODE_PREFIX + gid + ":" + fromUid;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(slowKey,
                    String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(slowModeInterval));
            if (Boolean.FALSE.equals(acquired)) {
                String lastSend = redisTemplate.opsForValue().get(slowKey);
                if (lastSend != null) {
                    try {
                        long elapsed = System.currentTimeMillis() - Long.parseLong(lastSend);
                        long remaining = slowModeInterval * 1000L - elapsed;
                        if (remaining > 0) {
                            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                                    "Too fast, please wait " + (remaining / 1000 + 1) + " seconds");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                redisTemplate.opsForValue().setIfAbsent(slowKey,
                        String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(slowModeInterval));
            }
        }

        List<Long> mentionedUids = parseMentionedUids(content, membership);
        String mentionedUidsStr = mentionedUids.stream()
                .map(String::valueOf).collect(Collectors.joining(","));

        validateMessageContent(msgType, content);
        content = sensitiveWordFilter.filter(content);

        String toId = String.valueOf(gid);
        String seqKey = RedisKeyConstants.SEQ_PREFIX + RedisKeyConstants.SEQ_GROUP_SUFFIX + gid;
        Long seq = atomicIncrementSeq(seqKey, messageMapper.getMaxGroupSeq(gid));

        Message message = new Message();
        message.setMsgId(idGenerator.nextId());
        message.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
        message.setFromUid(fromUid);
        message.setToId(toId);
        message.setMsgType(msgType);
        message.setContent(content);
        message.setMentionedUids(mentionedUidsStr.isEmpty() ? null : mentionedUidsStr);
        message.setStatus(1);
        message.setSeq(seq);
        message.setCreatedAt(LocalDateTime.now());
        message.setReplyToMsgId(replyToMsgId);

        Map<String, Integer> countResult = groupFeignClient.getMemberCount(gid);
        int memberCount = countResult != null ? countResult.getOrDefault(BusinessConstants.MEMBER_COUNT_KEY, 0) : 0;
        List<Long> memberUids = groupFeignClient.getMemberUids(gid);
        List<Long> receiverUids = memberUids.stream()
                .filter(memberUid -> !memberUid.equals(fromUid))
                .toList();
        boolean fanOut = memberCount <= BusinessConstants.GROUP_FANOUT_THRESHOLD;

        // 通过RocketMQ事务消息持久化MySQL行；MySQL提交后直接执行ES索引、Redis更新和WebSocket推送。
        rocketMqProducer.publish(PendingMessageContext.builder()
                .message(message)
                .groupReceiverUids(receiverUids)
                .groupFanOut(fanOut)
                .build());

        indexToEs(message);
        if (fanOut && !receiverUids.isEmpty()) {
            bumpGroupUnreadCounters(receiverUids, toId);
        }
        pushService.echoToSender(fromUid, message);
        pushService.pushGroupMessage(memberUids, fromUid, message, mentionedUids);

        return message;
    }

    /**
     * 在一次流水线往返中递增所有群接收者的Redis未读计数器。
     *
     * @param receiverUids 除发送者外的成员列表
     * @param targetId     群组ID（字符串形式）
     */
    private void bumpGroupUnreadCounters(List<Long> receiverUids, String targetId) {
        long expireSeconds = BusinessConstants.REDIS_DEFAULT_TTL.toSeconds();
        redisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    org.springframework.data.redis.connection.StringRedisConnection src =
                            (org.springframework.data.redis.connection.StringRedisConnection) connection;
                    for (Long uid : receiverUids) {
                        String key = unreadKey(uid, BusinessConstants.SESSION_TYPE_GROUP, targetId);
                        src.incr(key);
                        src.expire(key, expireSeconds);
                    }
                    return null;
                });
    }

    /**
     * 查询群聊历史，按序号分页。验证成员身份。
     *
     * @param uid       请求用户
     * @param gid       群组ID
     * @param beforeSeq 分页游标（首页传null）
     * @param limit     最大返回数
     * @return 按序号降序排列的群聊历史消息列表
     * @throws BusinessException 如果不是群成员
     */
    @Override
    public List<Message> getGroupHistory(Long uid, Long gid, Long beforeSeq, int limit) {
        Map<String, Object> membership = groupFeignClient.checkMembership(gid, uid);
        if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a group member, cannot view messages");
        }

        Conversation conv = conversationMapper.findByUidAndTarget(uid, BusinessConstants.SESSION_TYPE_GROUP, String.valueOf(gid));
        if (conv == null) {
            conv = new Conversation();
            conv.setId(idGenerator.nextId());
            conv.setUid(uid);
            conv.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
            conv.setTargetId(String.valueOf(gid));
            conv.setUnreadCount(0);
            conv.setIsPinned(0);
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conv);
        }

        return messageMapper.findGroupHistory(gid, beforeSeq, limit, uid);
    }

    /**
     * 在单聊或群聊会话中置顶消息。重复置顶会被拒绝。
     *
     * @param uid            置顶用户
     * @param targetUid      会话对方
     * @param msgId          要置顶的消息ID
     * @param contentSummary 置顶内容的简短摘要
     * @throws BusinessException 如果消息不存在或已置顶
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinMessage(Long uid, Long targetUid, Long msgId, String contentSummary) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }

        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(message.getSessionType())) {
            if (!uid.equals(message.getFromUid()) && !String.valueOf(uid).equals(message.getToId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "No permission to pin this message");
            }
            if (uid.equals(message.getFromUid())) {
                targetUid = Long.parseLong(message.getToId());
            } else {
                targetUid = message.getFromUid();
            }
            String targetId = String.valueOf(targetUid);
            PinnedMessage existing = pinnedMessageMapper.findBySessionAndMsg(BusinessConstants.SESSION_TYPE_SINGLE, targetId, msgId);
            if (existing != null) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "Message already pinned");
            }

            PinnedMessage pinned = new PinnedMessage();
            pinned.setId(idGenerator.nextId());
            pinned.setSessionType(BusinessConstants.SESSION_TYPE_SINGLE);
            pinned.setTargetId(targetId);
            pinned.setMsgId(msgId);
            pinned.setPinnedBy(uid);
            pinned.setContentSummary(contentSummary);
            pinned.setPinnedAt(LocalDateTime.now());
            pinnedMessageMapper.insert(pinned);
        } else {
            Long gid = Long.parseLong(message.getToId());
            Map<String, Object> membership = groupFeignClient.checkMembership(gid, uid);
            if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
            }
            String role = (String) membership.get(BusinessConstants.MEMBERSHIP_KEY_ROLE);
            if (!BusinessConstants.ROLE_OWNER.equals(role) && !BusinessConstants.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can pin messages");
            }
            PinnedMessage existing = pinnedMessageMapper.findBySessionAndMsg(BusinessConstants.SESSION_TYPE_GROUP, message.getToId(), msgId);
            if (existing != null) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "Message already pinned");
            }

            PinnedMessage pinned = new PinnedMessage();
            pinned.setId(idGenerator.nextId());
            pinned.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
            pinned.setTargetId(message.getToId());
            pinned.setMsgId(msgId);
            pinned.setPinnedBy(uid);
            pinned.setContentSummary(contentSummary);
            pinned.setPinnedAt(LocalDateTime.now());
            pinnedMessageMapper.insert(pinned);
        }
    }

    /**
     * 原子递增会话的Redis未读计数器并刷新TTL.
     * 未读计数在Redis中作为唯一实时数据源跟踪; the
     * conversation.unread_count列仅是由
     * {@code UnreadSyncTask} 维护的定期快照，在Redis无条目时用作降级方案.
     */
    private void incrementUnreadInRedis(Long uid, String sessionType, String targetId) {
        String key = unreadKey(uid, sessionType, targetId);
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            org.springframework.data.redis.connection.StringRedisConnection src =
                    (org.springframework.data.redis.connection.StringRedisConnection) connection;
            src.incr(key);
            src.expire(key, BusinessConstants.REDIS_DEFAULT_TTL.getSeconds());
            return null;
        });
    }

    /**
     * 构建单聊序号的复合Redis键，
     * UID按其自然顺序排序。
     *
     * @param uid1 第一个用户ID
     * @param uid2 第二个用户ID
     * @return 复合seq键，格式为 {@code seq:single:{min}:{max}}
     */
    private String buildSeqKey(Long uid1, Long uid2) {
        long min = Math.min(uid1, uid2);
        long max = Math.max(uid1, uid2);
        return RedisKeyConstants.SEQ_PREFIX + RedisKeyConstants.SEQ_SINGLE_SUFFIX + min + ":" + max;
    }

    /**
     * 通过 {@link #SEQ_INCREMENT_SCRIPT} 原子递增序号。
     * 如果键不存在，则在递增前用maxSeqFallback初始化；
     * 初始化、递增和TTL刷新在Lua脚本中原子完成，
     * 并发发送者无法在存在检查和初始化写入之间竞争。
     *
     * @param seqKey          Redis序号键
     * @param maxSeqFallback  MySQL中已有的最大seq，键不存在时作为种子值
     * @return 下一个序号
     */
    private Long atomicIncrementSeq(String seqKey, Long maxSeqFallback) {
        long seed = maxSeqFallback != null && maxSeqFallback > 0 ? maxSeqFallback : 0L;
        Long newSeq = redisTemplate.execute(SEQ_INCREMENT_SCRIPT,
                Collections.singletonList(seqKey),
                String.valueOf(seed),
                String.valueOf(SEQ_KEY_TTL.toSeconds()));
        if (newSeq == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "Failed to allocate message seq");
        }
        return newSeq;
    }

    /** 匹配单个@uid提及的正则: <@digits> */
    private static final Pattern MENTION_PATTERN = Pattern.compile("<@(\\d+)>");
    /** @all提及的字面标记；仅owner/admin可使用 */
    private static final String MENTION_ALL = "<@all>";

    /**
     * 从消息内容中解析@提及的UID。验证@all仅由
     * 所有者/管理员使用。返回被提及的用户ID列表。
     */
    private List<Long> parseMentionedUids(String content, Map<String, Object> membership) {
        List<Long> uids = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return uids;
        }
        if (content.contains(MENTION_ALL)) {
            String role = (String) membership.get(BusinessConstants.MEMBERSHIP_KEY_ROLE);
            if (!BusinessConstants.ROLE_OWNER.equals(role) && !BusinessConstants.ROLE_ADMIN.equals(role)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can use @all");
            }
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                uids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return uids;
    }

    /** 共享的ObjectMapper实例，用于JSON验证 */
    private static final String FIELD_NAME = "name";
    private static final String FIELD_URL = "url";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 根据msgType验证消息内容结构。TEXT始终有效；
     * IMAGE期望JSON数组；FILE/VOICE/VIDEO期望包含必需字段的JSON对象。
     */
    @SuppressWarnings("unchecked")
    private void validateMessageContent(String msgType, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Message content cannot be empty");
        }
        try {
            switch (msgType) {
                case BusinessConstants.MSG_TYPE_TEXT:
                    return;
                case BusinessConstants.MSG_TYPE_IMAGE:
                    List<Object> images = OBJECT_MAPPER.readValue(content, List.class);
                    if (images.isEmpty()) {
                        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Image message must contain at least one image");
                    }
                    break;
                case BusinessConstants.MSG_TYPE_FILE:
                    Map<String, Object> file = OBJECT_MAPPER.readValue(content, Map.class);
                    if (file.get(FIELD_NAME) == null || file.get(FIELD_URL) == null) {
                        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "File message must contain name and url");
                    }
                    break;
                case BusinessConstants.MSG_TYPE_VOICE:
                    Map<String, Object> voice = OBJECT_MAPPER.readValue(content, Map.class);
                    if (voice.get(FIELD_URL) == null) {
                        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Voice message must contain url");
                    }
                    break;
                case BusinessConstants.MSG_TYPE_VIDEO:
                    Map<String, Object> video = OBJECT_MAPPER.readValue(content, Map.class);
                    if (video.get(FIELD_URL) == null) {
                        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Video message must contain url");
                    }
                    break;
                default:
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Unsupported message type: " + msgType);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Invalid message content format, check JSON");
        }
    }

    @Override
    public List<Map<String, Object>> getPinnedMessages(Long uid, String sessionType, String targetId) {
        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(sessionType)) {
            // 无需显式参与者检查：会话存在于调用者uid的conversation表中，
            // getHistory同样没有此类检查。置顶消息是会话语境元数据，
            // 不需要持续好友关系。
        } else {
            Map<String, Object> membership = groupFeignClient.checkMembership(Long.parseLong(targetId), uid);
            if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
            }
        }
        List<PinnedMessage> pinned = pinnedMessageMapper.findBySession(sessionType, targetId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PinnedMessage p : pinned) {
            Map<String, Object> item = new LinkedHashMap<>(16);
            item.put("msgId", String.valueOf(p.getMsgId()));
            item.put("contentSummary", p.getContentSummary());
            item.put("pinnedBy", String.valueOf(p.getPinnedBy()));
            item.put("pinnedAt", p.getPinnedAt());
            Message msg = messageMapper.findByMsgId(p.getMsgId());
            if (msg != null && msg.getIsRecalled() != 1) {
                item.put("content", msg.getContent());
                item.put("msgType", msg.getMsgType());
            }
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpinMessage(Long uid, Long msgId) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }
        PinnedMessage pinned = pinnedMessageMapper.findBySessionAndMsg(
                message.getSessionType(), message.getToId(), msgId);
        if (pinned == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Pin record not found");
        }
        if (BusinessConstants.SESSION_TYPE_GROUP.equals(message.getSessionType())) {
            if (!uid.equals(pinned.getPinnedBy())) {
                Map<String, Object> membership = groupFeignClient.checkMembership(
                        Long.parseLong(message.getToId()), uid);
                String role = (String) membership.get(BusinessConstants.MEMBERSHIP_KEY_ROLE);
                if (!BusinessConstants.ROLE_OWNER.equals(role) && !BusinessConstants.ROLE_ADMIN.equals(role)) {
                    throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                            "Only the pinner, owner, or admin can unpin");
                }
            }
        } else {
            if (!uid.equals(pinned.getPinnedBy()) &&
                    !uid.equals(message.getFromUid()) &&
                    !String.valueOf(uid).equals(message.getToId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                        "No permission to unpin this message");
            }
        }
        pinnedMessageMapper.deleteById(pinned.getId());
        log.info("User {} unpinned message {}", uid, msgId);
    }

    @Override
    public List<Message> getMessagesAfterSeq(Long uid, String sessionType, String targetId, Long afterSeq) {
        if (BusinessConstants.SESSION_TYPE_GROUP.equals(sessionType)) {
            Map<String, Object> membership = groupFeignClient.checkMembership(Long.parseLong(targetId), uid);
            if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
            }
        } else {
            Long peerUid = Long.parseLong(targetId);
            if (!uid.equals(peerUid)) {
                Map<String, Boolean> result = userFeignClient.checkFriendship(uid, peerUid);
                if (result == null || !Boolean.TRUE.equals(result.get(BusinessConstants.FRIENDSHIP_KEY_FRIENDS))) {
                    throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a participant of this conversation");
                }
            }
        }
        long after = afterSeq != null ? afterSeq : 0L;
        return messageMapper.findMessagesAfterSeq(sessionType, targetId, after, BusinessConstants.MESSAGE_PAGE_LIMIT, uid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendSystemGroupMessage(Long gid, Long fromUid, String content) {
        String toId = String.valueOf(gid);
        String seqKey = RedisKeyConstants.SEQ_PREFIX + RedisKeyConstants.SEQ_GROUP_SUFFIX + gid;
        Long seq = atomicIncrementSeq(seqKey, messageMapper.getMaxGroupSeq(gid));

        Message message = new Message();
        message.setMsgId(idGenerator.nextId());
        message.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
        message.setFromUid(fromUid);
        message.setToId(toId);
        message.setMsgType(BusinessConstants.MSG_TYPE_SYSTEM);
        message.setContent(content);
        message.setStatus(1);
        message.setSeq(seq);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);

        List<Long> memberUids = groupFeignClient.getMemberUids(gid);
        pushService.pushGroupMessage(memberUids, fromUid, message, Collections.emptyList());
        indexToEs(message);
        return message;
    }

    @Override
    public List<Message> getMessageContext(Long uid, Long msgId, int size) {
        Message target = messageMapper.findByMsgId(msgId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }

        String sessionType = target.getSessionType();
        String targetId = target.getToId();
        Long targetSeq = target.getSeq();

        List<Message> before = messageMapper.findMessagesBeforeSeq(
                sessionType, targetId, targetSeq, size, uid);
        Collections.reverse(before);

        List<Message> after = messageMapper.findMessagesAfterSeq(
                sessionType, targetId, targetSeq, size, uid);

        List<Message> result = new ArrayList<>(before.size() + after.size() + 1);
        result.addAll(before);
        result.add(target);
        result.addAll(after);
        return result;
    }

    private void indexToEs(Message message) {
        try {
            elasticsearchService.indexMessage(message);
        } catch (Exception e) {
            log.warn("Direct ES index skipped for msgId={}: {}", message.getMsgId(), e.getMessage());
        }
    }
}
