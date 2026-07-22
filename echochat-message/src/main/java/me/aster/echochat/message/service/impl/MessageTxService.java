package me.aster.echochat.message.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.entity.Conversation;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.mapper.ConversationMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mq.PendingMessageContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行RocketMQ事务发送的本地数据库事务：
 * 在单个原子单元中持久化聊天消息并创建/更新受影响的会话行。
 * 此处仅操作MySQL状态——Redis计数器和WebSocket推送由调用方在提交成功后执行。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTxService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 原子地持久化消息及其会话行。注意：非幂等——重复的消息ID将导致数据库唯一键冲突异常。
     *
     * @param ctx 待处理消息上下文
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void persist(PendingMessageContext ctx) {
        retryOnDeadlock(() -> persistInternal(ctx));
    }

    private void persistInternal(PendingMessageContext ctx) {
        Message message = ctx.getMessage();
        messageMapper.insert(message);

        if (BusinessConstants.SESSION_TYPE_SINGLE.equals(message.getSessionType())) {
            Long toUid = ctx.getSingleToUid();
            upsertConversation(message.getFromUid(), BusinessConstants.SESSION_TYPE_SINGLE, message.getToId(), message.getMsgId(), false);
            upsertConversation(toUid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(message.getFromUid()), message.getMsgId(), true);
        } else {
            upsertConversation(message.getFromUid(), BusinessConstants.SESSION_TYPE_GROUP, message.getToId(), message.getMsgId(), false);
            List<Long> receiverUids = ctx.getGroupReceiverUids();
            if (ctx.isGroupFanOut() && receiverUids != null && !receiverUids.isEmpty()) {
                fanOutGroupConversations(message.getToId(), receiverUids, message.getMsgId());
            }
        }
    }

    /**
     * 创建或更新单个会话行。unread_count列仅作为快照：
     * 实时未读跟踪在Redis中进行。
     */
    private void upsertConversation(Long uid, String sessionType, String targetId, Long msgId, boolean isReceiver) {
        Conversation conv = conversationMapper.findByUidAndTarget(uid, sessionType, targetId);
        LocalDateTime now = LocalDateTime.now();
        if (conv == null) {
            conv = new Conversation();
            conv.setId(idGenerator.nextId());
            conv.setUid(uid);
            conv.setSessionType(sessionType);
            conv.setTargetId(targetId);
            conv.setLastMsgId(msgId);
            conv.setUnreadCount(isReceiver ? 1 : 0);
            conv.setIsPinned(0);
            conv.setCreatedAt(now);
            conv.setUpdatedAt(now);
            conversationMapper.insert(conv);
        } else {
            Conversation updateConv = new Conversation();
            updateConv.setLastMsgId(msgId);
            updateConv.setUpdatedAt(now);
            conversationMapper.update(updateConv,
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation>()
                            .eq(Conversation::getUid, uid)
                            .eq(Conversation::getSessionType, sessionType)
                            .eq(Conversation::getTargetId, targetId));
        }
    }

    /**
     * 群接收者的批量写扩散：一条UPDATE更新已有行，再加一条多行INSERT插入缺失行，
     * 而非每个成员一条语句。
     */
    private void fanOutGroupConversations(String targetId, List<Long> receiverUids, Long msgId) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> existingUids = conversationMapper.findExistingGroupConversationUids(targetId, receiverUids);
        if (!existingUids.isEmpty()) {
            conversationMapper.batchTouchGroupConversations(targetId, msgId, now, existingUids);
        }

        Set<Long> existingSet = new HashSet<>(existingUids);
        List<Conversation> toInsert = new ArrayList<>();
        for (Long uid : receiverUids) {
            if (existingSet.contains(uid)) {
                continue;
            }
            Conversation conv = new Conversation();
            conv.setId(idGenerator.nextId());
            conv.setUid(uid);
            conv.setSessionType(BusinessConstants.SESSION_TYPE_GROUP);
            conv.setTargetId(targetId);
            conv.setLastMsgId(msgId);
            conv.setUnreadCount(1);
            conv.setIsPinned(0);
            conv.setCreatedAt(now);
            conv.setUpdatedAt(now);
            toInsert.add(conv);
        }
        if (!toInsert.isEmpty()) {
            conversationMapper.batchInsert(toInsert);
        }
    }

    /**
     * 检测到死锁时最多重试3次，使用短退避让竞争事务有时间提交。
     */
    private void retryOnDeadlock(Runnable operation) {
        for (int i = 0; i < 3; i++) {
            try {
                operation.run();
                return;
            } catch (org.springframework.dao.TransientDataAccessException e) {
                if (i == 2) {
                    throw e;
                }
                log.warn("Transient data access error detected, retrying ({}/3): {}", i + 1, e.getMessage());
                try { Thread.sleep(20L * (i + 1)); } catch (InterruptedException ignored) {}
            }
        }
    }
}