package me.aster.echochat.message.service;

import java.util.List;
import java.util.Map;

/**
 * 会话管理服务接口：列表、标记已读、置顶和删除。
 * @author AsterWinston
 */
public interface ConversationService {

    /**
     * 获取指定用户的会话列表。
     *
     * @param uid 用户
     * @return 包含最后消息和对端信息的会话摘要列表
     */
    List<Map<String, Object>> getConversations(Long uid);

    /**
     * 将会话标记为已读，重置未读计数。
     *
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    会话对端/群组ID
     */
    void markAsRead(Long uid, String sessionType, String targetId);

    /**
     * 置顶或取消置顶指定会话。
     *
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    会话对端/群组ID
     * @param pinned      true=置顶，false=取消置顶
     */
    void pinConversation(Long uid, String sessionType, String targetId, boolean pinned);

    /**
     * 设置会话的免打扰模式。
     *
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    会话对端/群组ID
     * @param dnd         true=开启免打扰，false=关闭
     */
    void setDnd(Long uid, String sessionType, String targetId, boolean dnd);

    /**
     * 删除指定用户的会话记录。
     *
     * @param uid         用户
     * @param sessionType 会话类型（single、group）
     * @param targetId    要删除的会话对端/群组ID
     */
    void deleteConversation(Long uid, String sessionType, String targetId);
}