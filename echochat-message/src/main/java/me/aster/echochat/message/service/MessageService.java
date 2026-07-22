package me.aster.echochat.message.service;

import me.aster.echochat.message.entity.Message;
import java.util.List;
import java.util.Map;

/**
 * 核心消息操作的服务接口：发送、历史记录、撤回、删除、转发、回复和置顶。
 * @author AsterWinston
 */
public interface MessageService {

    /**
     * @param fromUid 发送者
     * @param toUid   接收者
     * @param msgType 消息内容类型
     * @param content 消息正文
     * @return 持久化后的{@link Message}
     */
    Message sendMessage(Long fromUid, Long toUid, String msgType, String content);

    /**
     * 发送带有可选回复引用的消息，确保replyToMsgId包含在WebSocket推送中。
     *
     * @param fromUid      发送者
     * @param toUid        接收者
     * @param msgType      消息内容类型
     * @param content      消息正文
     * @param replyToMsgId 被回复的消息ID（可为null）
     * @return 持久化后的{@link Message}
     */
    Message sendMessage(Long fromUid, Long toUid, String msgType, String content, Long replyToMsgId);

    /**
     * @param uid       请求用户
     * @param targetUid 会话对端
     * @param beforeSeq 分页游标（可为null）
     * @param limit     最大结果数
     * @return 聊天历史列表
     */
    List<Message> getHistory(Long uid, Long targetUid, Long beforeSeq, int limit);

    /**
     * @param uid   请求用户（必须是原始发送者）
     * @param msgId 要撤回的消息
     * @return 更新后的{@link Message}
     */
    Message recallMessage(Long uid, Long msgId);

    /**
     * @param uid   请求用户（必须是发送者或接收者）
     * @param msgId 要删除的消息
     */
    void deleteMessage(Long uid, Long msgId);

    /**
     * @param fromUid 转发者
     * @param toUid   接收者
     * @param msgId   要转发的原始消息
     * @return 转发后的{@link Message}
     */
    Message forwardMessage(Long fromUid, Long toUid, Long msgId);

    /**
     * @param fromUid 转发者
     * @param gid     目标群组ID
     * @param msgId   要转发的原始消息
     * @return 转发后的{@link Message}
     */
    Message forwardGroupMessage(Long fromUid, Long gid, Long msgId);

    /**
     * @param fromUid      回复者
     * @param toUid        接收者
     * @param replyToMsgId 被回复的消息
     * @param content      回复内容
     * @return 回复的{@link Message}
     */
    Message replyMessage(Long fromUid, Long toUid, Long replyToMsgId, String content);

    /**
     * @param uid            置顶消息的用户
     * @param targetUid      会话中的对端
     * @param msgId          要置顶的消息
     * @param contentSummary 置顶内容的简短摘要
     */
    void pinMessage(Long uid, Long targetUid, Long msgId, String contentSummary);

    void unpinMessage(Long uid, Long msgId);

    /**
     * 发送带有可选回复引用的群消息。
     *
     * @param fromUid      发送者
     * @param gid          目标群组ID
     * @param msgType      消息内容类型
     * @param content      消息正文
     * @param replyToMsgId 被回复的消息ID（可为null）
     * @return 持久化后的{@link Message}
     */
    Message sendGroupMessage(Long fromUid, Long gid, String msgType, String content, Long replyToMsgId);

    /**
     * @param uid       请求用户
     * @param gid       群组ID
     * @param beforeSeq 分页游标（可为null）
     * @param limit     最大结果数
     * @return 群聊历史列表
     */
    List<Message> getGroupHistory(Long uid, Long gid, Long beforeSeq, int limit);

    List<Map<String, Object>> getPinnedMessages(Long uid, String sessionType, String targetId);

    List<Message> getMessagesAfterSeq(Long uid, String sessionType, String targetId, Long afterSeq);

    Message sendSystemGroupMessage(Long gid, Long fromUid, String content);

    List<Message> getMessageContext(Long uid, Long msgId, int size);
}