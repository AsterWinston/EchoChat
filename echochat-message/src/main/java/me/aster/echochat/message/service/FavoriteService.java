package me.aster.echochat.message.service;

import me.aster.echochat.message.entity.Favorite;
import java.util.List;
import java.util.Map;

/**
 * 收藏（消息收藏）操作的服务接口：添加、移除、批量删除和列表。
 * @author AsterWinston
 */
public interface FavoriteService {

    /**
     * 为指定用户收藏一条消息。
     *
     * @param uid   收藏消息的用户
     * @param msgId 要收藏的消息ID
     * @return 创建的{@link Favorite}记录
     */
    Favorite addFavorite(Long uid, Long msgId);

    /**
     * 移除单条收藏（取消收藏消息）。
     *
     * @param uid   拥有该收藏的用户
     * @param msgId 要取消收藏的消息ID
     */
    void removeFavorite(Long uid, Long msgId);

    /**
     * 批量删除指定用户的多个收藏。
     *
     * @param uid    拥有这些收藏的用户
     * @param msgIds 要取消收藏的消息ID列表
     */
    void removeFavorites(Long uid, List<Long> msgIds);

    /**
     * 列出用户的收藏，可按消息类型筛选。
     * 每项包含消息的完整内容作为内容预览。
     *
     * @param uid     用户ID
     * @param msgType 可选的消息内容类型筛选，null表示所有类型
     * @return 包含键id、msgId、msgType、msgSummary、content、fromUid、collectedAt的map列表
     */
    List<Map<String, Object>> getFavorites(Long uid, String msgType);
}