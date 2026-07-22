package me.aster.echochat.search.service;

import me.aster.echochat.search.entity.GroupDocument;
import me.aster.echochat.search.entity.MessageDocument;
import me.aster.echochat.search.entity.UserDocument;

import java.util.List;

/**
 * 基于Elasticsearch的消息、用户和群组搜索操作服务接口。
 * @author AsterWinston
 */
public interface SearchService {

    /**
     * 搜索当前用户的消息。
     *
     * @param uid     用户ID
     * @param keyword 搜索关键词
     * @return 匹配的消息文档列表
     */
    List<MessageDocument> searchMessages(Long uid, String keyword);

    /**
     * 在指定用户的会话中搜索消息。
     *
     * @param uid       当前用户ID
     * @param targetUid 会话对方的用户ID
     * @param keyword   搜索关键词
     * @return 匹配的消息文档列表
     */
    List<MessageDocument> searchMessagesWithUser(Long uid, Long targetUid, String keyword);

    /**
     * 搜索用户。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户文档列表
     */
    List<UserDocument> searchUsers(String keyword);

    /**
     * 搜索群组。
     *
     * @param keyword 搜索关键词
     * @return 匹配的群组文档列表
     */
    List<GroupDocument> searchGroups(String keyword);
}
