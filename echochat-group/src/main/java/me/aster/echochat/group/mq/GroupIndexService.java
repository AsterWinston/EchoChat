package me.aster.echochat.group.mq;

import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.group.client.SearchFeignClient;
import me.aster.echochat.group.entity.GroupInfo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步服务，在群组创建或更新后通过search模块的Feign客户端将 {@link GroupInfo} 数据同步到Elasticsearch。
 * @author AsterWinston
 */
@Slf4j
@Component
public class GroupIndexService {

    private final SearchFeignClient searchFeignClient;

    public GroupIndexService(SearchFeignClient searchFeignClient) {
        this.searchFeignClient = searchFeignClient;
    }

    @Async
    public void syncGroupToEs(GroupInfo group) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(16);
            body.put("gid", group.getGid());
            body.put("name", group.getName());
            body.put("ownerUid", group.getOwnerUid());
            body.put("avatar", group.getAvatar());
            searchFeignClient.indexGroup(body);
        } catch (Exception e) {
            log.error("ES indexing failed for gid={}: {}", group.getGid(), e.getMessage());
        }
    }
}
