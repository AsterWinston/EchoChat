package me.aster.echochat.message.service;

import java.util.List;
import java.util.Map;

/**
 * 会话管理服务接口：列表、标记已读、置顶和删除。
 * @author AsterWinston
 */
public interface ConversationService {

    /**
     * @param uid 用户
     * @return 包含最后消息和对端信息的会话摘要列表
     */
    List<Map<String, Object>> getConversations(Long uid);

    /**
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    会话对端/群组ID
     */
    void markAsRead(Long uid, String sessionType, String targetId);

    /**
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    会话对端/群组ID
     * @param pinned      true=置顶，false=取消置顶
     */
    void pinConversation(Long uid, String sessionType, String targetId, boolean pinned);

    void setDnd(Long uid, String sessionType, String targetId, boolean dnd);

    /**
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    要删除的会话对端/群组ID
     */
    void deleteConversation(Long uid, String sessionType, String targetId);
}