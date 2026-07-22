package me.aster.echochat.message.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.message.client.GroupFeignClient;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.entity.MessageRead;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.mapper.MessageReadMapper;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 负责标记消息已读、持久化已读回执，
 * 并通过WebSocket向发送者推送已读回执通知的服务。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final MessageMapper messageMapper;
    private final MessageReadMapper messageReadMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final WebSocketPushService pushService;
    private final GroupFeignClient groupFeignClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * 将发送者在指定序号及之前的所有未读消息标记为已读，
     * 如果发送者在线则推送已读回执。
     *
     * @param readerUid 已读消息的用户
     * @param senderUid 原始发送者
     * @param toSeq     标记已读的序号上限（包含）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markMessagesAsRead(Long readerUid, Long senderUid, Long toSeq) {
        List<Message> messages = messageMapper.findUnreadMessages(senderUid, String.valueOf(readerUid), toSeq);
        if (messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            if (message.getStatus() == null || message.getStatus() < 2) {
                message.setStatus(2);
                messageMapper.updateById(message);

                MessageRead read = new MessageRead();
                read.setId(idGenerator.nextId());
                read.setMsgId(message.getMsgId());
                read.setUid(readerUid);
                read.setReadAt(LocalDateTime.now());
                messageReadMapper.insert(read);
            }
        }

        log.info("Read receipt: reader={}, sender={}, toSeq={}, count={}", readerUid, senderUid, toSeq, messages.size());

        // 跨节点感知：pushReadReceipt通过投递网关路由，
        // 当发送者没有任何在线连接时静默丢弃帧。
        pushService.pushReadReceipt(senderUid, readerUid, toSeq);
    }

    public Map<String, Object> getGroupReadStatus(Long msgId, Long requestUid) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }
        if (BusinessConstants.SESSION_TYPE_GROUP.equals(message.getSessionType())) {
            Map<String, Object> membership = groupFeignClient.checkMembership(Long.parseLong(message.getToId()), requestUid);
            if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
            }
        } else {
            if (!requestUid.equals(message.getFromUid()) && !String.valueOf(requestUid).equals(message.getToId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "No permission to view read status");
            }
        }
        Long senderUid = message.getFromUid();
        Set<String> readUids;
        int readCount;
        if (BusinessConstants.SESSION_TYPE_GROUP.equals(message.getSessionType())) {
            Long gid = Long.parseLong(message.getToId());
            List<Long> memberUids = groupFeignClient.getMemberUids(gid);
            List<Long> candidates = memberUids.stream()
                    .filter(memberUid -> !memberUid.equals(senderUid))
                    .toList();
            readUids = new LinkedHashSet<>();
            if (!candidates.isEmpty()) {
                String readKey = RedisKeyConstants.READ_BITMAP_PREFIX + msgId;
                String idxKey = RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + gid;

                // 对所有成员索引使用一次HMGET，而非每个成员一次HGET。
                List<Object> fields = candidates.stream()
                        .map(uid -> (Object) String.valueOf(uid))
                        .collect(Collectors.toList());
                List<Object> idxValues = redisTemplate.opsForHash().multiGet(idxKey, fields);
                long[] offsets = new long[candidates.size()];
                for (int i = 0; i < candidates.size(); i++) {
                    Object idx = i < idxValues.size() ? idxValues.get(i) : null;
                    if (idx == null) {
                        Long newIdx = redisTemplate.opsForValue().increment(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + RedisKeyConstants.SEQ_PREFIX + gid);
                        redisTemplate.opsForHash().put(idxKey, String.valueOf(candidates.get(i)), String.valueOf(newIdx));
                        offsets[i] = newIdx != null ? newIdx : 0L;
                    } else {
                        offsets[i] = Long.parseLong(idx.toString());
                    }
                }

                // 一次流水线往返完成所有GETBIT探测。
                String finalReadKey = readKey;
                List<Object> bits = redisTemplate.executePipelined(
                        (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                            org.springframework.data.redis.connection.StringRedisConnection src =
                                    (org.springframework.data.redis.connection.StringRedisConnection) connection;
                            for (long offset : offsets) {
                                src.getBit(finalReadKey, offset);
                            }
                            return null;
                        });
                for (int i = 0; i < candidates.size(); i++) {
                    Object bit = i < bits.size() ? bits.get(i) : null;
                    if (Boolean.TRUE.equals(bit)) {
                        readUids.add(String.valueOf(candidates.get(i)));
                    }
                }
            }
            if (readUids.isEmpty()) {
                List<MessageRead> reads = messageReadMapper.findByMsgId(msgId);
                readUids = reads.stream()
                        .filter(r -> !r.getUid().equals(senderUid))
                        .map(r -> String.valueOf(r.getUid()))
                        .collect(Collectors.toSet());
            }
            readCount = readUids.size();
        } else {
            List<MessageRead> reads = messageReadMapper.findByMsgId(msgId);
            readUids = reads.stream()
                    .filter(r -> !r.getUid().equals(senderUid))
                    .map(r -> String.valueOf(r.getUid()))
                    .collect(Collectors.toSet());
            readCount = readUids.size();
        }
        Map<String, Object> result = new LinkedHashMap<>(16);
        result.put("readCount", readCount);
        result.put("readUids", readUids);
        return result;
    }
}