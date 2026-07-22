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

    List<MessageDocument> searchMessages(Long uid, String keyword);

    List<MessageDocument> searchMessagesWithUser(Long uid, Long targetUid, String keyword);

    List<UserDocument> searchUsers(String keyword);

    List<GroupDocument> searchGroups(String keyword);
}
