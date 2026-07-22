package me.aster.echochat.search.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.search.client.GroupFeignClient;
import me.aster.echochat.search.client.MessageFeignClient;
import me.aster.echochat.search.client.UserFeignClient;
import me.aster.echochat.search.entity.GroupDocument;
import me.aster.echochat.search.entity.MessageDocument;
import me.aster.echochat.search.entity.UserDocument;
import me.aster.echochat.search.service.SearchService;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link SearchService}的实现，使用Spring Data Elasticsearch的{@link Criteria}查询。
 * 用户和群组搜索在ES无结果时回退到通过Feign调用MySQL。
 * @author AsterWinston
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations operations;
    private final UserFeignClient userFeignClient;
    private final GroupFeignClient groupFeignClient;
    private final MessageFeignClient messageFeignClient;

    public SearchServiceImpl(ElasticsearchOperations operations,
                             UserFeignClient userFeignClient,
                             GroupFeignClient groupFeignClient,
                             MessageFeignClient messageFeignClient) {
        this.operations = operations;
        this.userFeignClient = userFeignClient;
        this.groupFeignClient = groupFeignClient;
        this.messageFeignClient = messageFeignClient;
    }

    /**
     * @param uid     当前用户ID
     * @param keyword 要在消息内容中匹配的搜索关键词
     * @return 匹配的{@link MessageDocument}实例，用户是发送方或接收方
     */
    @Override
    public List<MessageDocument> searchMessages(Long uid, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        Criteria contentCriteria = new Criteria("content").matches(keyword);
        Criteria senderCriteria = new Criteria("fromUid").is(uid);
        Criteria receiverCriteria = new Criteria("toId").is(String.valueOf(uid));
        Criteria directionCriteria = senderCriteria.or(receiverCriteria);

        CriteriaQuery query = new CriteriaQuery(contentCriteria.and(directionCriteria)
                .and(new Criteria("isRecalled").not().is(1)));
        query.setMaxResults(50);

        try {
            SearchHits<MessageDocument> hits = operations.search(query, MessageDocument.class);
            List<MessageDocument> docs = filterDeleted(uid, hits.stream().map(SearchHit::getContent).collect(Collectors.toList()));
            enrichDisplayNames(docs);
            return docs;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("ES search unavailable for messages: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * @param uid       当前用户ID
     * @param targetUid 对方参与者的用户ID
     * @param keyword   要在消息内容中匹配的搜索关键词
     * @return 两个用户之间匹配的{@link MessageDocument}实例
     */
    @Override
    public List<MessageDocument> searchMessagesWithUser(Long uid, Long targetUid, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        String uidStr = String.valueOf(uid);
        String targetStr = String.valueOf(targetUid);

        Criteria contentCriteria = new Criteria("content").matches(keyword);

        Criteria direction1 = new Criteria("fromUid").is(uid).and("toId").is(targetStr);
        Criteria direction2 = new Criteria("fromUid").is(targetUid).and("toId").is(uidStr);
        Criteria directionCriteria = direction1.or(direction2);

        CriteriaQuery query = new CriteriaQuery(contentCriteria.and(directionCriteria)
                .and(new Criteria("isRecalled").not().is(1)));
        query.setMaxResults(50);

        try {
            SearchHits<MessageDocument> hits = operations.search(query, MessageDocument.class);
            List<MessageDocument> docs = filterDeleted(uid, hits.stream().map(SearchHit::getContent).collect(Collectors.toList()));
            enrichDisplayNames(docs);
            return docs;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("ES search unavailable for messages with user: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * @param keyword 搜索关键词
     * @return 匹配的{@link UserDocument}实例，ES无结果时回退到通过Feign调用MySQL
     */
    @Override
    public List<UserDocument> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        Criteria criteria = new Criteria("nickname").matches(keyword)
                .or("email").matches(keyword);

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setMaxResults(50);

        List<UserDocument> docs;
        try {
            SearchHits<UserDocument> hits = operations.search(query, UserDocument.class);
            docs = hits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("ES search unavailable for users: {}", e.getMessage());
            docs = Collections.emptyList();
        }
        if (!docs.isEmpty()) {
            return docs;
        }

        try {
            List<User> users = userFeignClient.searchUsers(keyword);
            if (users != null && !users.isEmpty()) {
                return users.stream().map(u -> {
                    UserDocument doc = new UserDocument();
                    doc.setUid(u.getUid());
                    doc.setNickname(u.getNickname());
                    doc.setEmail(u.getEmail());
                    return doc;
                }).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("User search fallback to MySQL failed: {}", e.getMessage());
        }
        return docs;
    }

    @Override
    public List<GroupDocument> searchGroups(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        Criteria criteria = new Criteria("name").matches(keyword);
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setMaxResults(50);

        List<GroupDocument> docs;
        try {
            SearchHits<GroupDocument> hits = operations.search(query, GroupDocument.class);
            docs = hits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("ES search unavailable for groups: {}", e.getMessage());
            docs = Collections.emptyList();
        }
        if (!docs.isEmpty()) {
            return docs;
        }

        try {
            return groupFeignClient.searchGroups(keyword);
        } catch (Exception e) {
            log.warn("Group search fallback to MySQL failed: {}", e.getMessage());
            return docs;
        }
    }

    private void enrichDisplayNames(List<MessageDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        Set<Long> uids = new HashSet<>();
        Set<Long> gids = new HashSet<>();
        for (MessageDocument doc : docs) {
            uids.add(doc.getFromUid());
            if (BusinessConstants.SESSION_TYPE_GROUP.equalsIgnoreCase(doc.getSessionType())) {
                try {
                    gids.add(Long.parseLong(doc.getToId()));
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    uids.add(Long.parseLong(doc.getToId()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Map<Long, String> userNames = new HashMap<>(16);
        try {
            List<User> users = userFeignClient.getUsersByUids(new ArrayList<>(uids));
            for (User u : users) {
                if (u != null) {
                    userNames.put(u.getUid(), u.getNickname());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user names for search: {}", e.getMessage());
        }

        Map<Long, String> groupNames = new HashMap<>(16);
        if (!gids.isEmpty()) {
            try {
                Map<String, Map<String, Object>> groups = groupFeignClient.getGroupInfoBatch(new ArrayList<>(gids));
                for (Map.Entry<String, Map<String, Object>> entry : groups.entrySet()) {
                    String name = (String) entry.getValue().get("name");
                    if (name != null) {
                        groupNames.put(Long.parseLong(entry.getKey()), name);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve group names for search: {}", e.getMessage());
            }
        }

        for (MessageDocument doc : docs) {
            doc.setFromName(userNames.get(doc.getFromUid()));
            if (BusinessConstants.SESSION_TYPE_GROUP.equalsIgnoreCase(doc.getSessionType())) {
                try {
                    doc.setToName(groupNames.get(Long.parseLong(doc.getToId())));
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    doc.setToName(userNames.get(Long.parseLong(doc.getToId())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private List<MessageDocument> filterDeleted(Long uid, List<MessageDocument> docs) {
        if (docs.isEmpty()) {
            return docs;
        }
        try {
            List<Long> msgIds = docs.stream().map(MessageDocument::getMsgId).toList();
            Set<Long> deleted = new HashSet<>(messageFeignClient.getDeletedMsgIds(
                    Map.of("uid", uid, "msgIds", msgIds)));
            return docs.stream().filter(d -> !deleted.contains(d.getMsgId())).toList();
        } catch (Exception e) {
            return docs;
        }
    }
}
