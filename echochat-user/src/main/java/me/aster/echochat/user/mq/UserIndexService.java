package me.aster.echochat.user.mq;

import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.user.client.SearchFeignClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步服务，在用户注册或更新后通过搜索模块的 Feign 客户端将 {@link User} 个人资料数据同步到 Elasticsearch。
 * @author AsterWinston
 */
@Slf4j
@Component
public class UserIndexService {

    private final SearchFeignClient searchFeignClient;

    public UserIndexService(SearchFeignClient searchFeignClient) {
        this.searchFeignClient = searchFeignClient;
    }

    /**
     * 异步将用户数据同步到 Elasticsearch。
     *
     * @param user 要索引的用户
     */
    @Async
    public void syncUserToEs(User user) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(16);
            body.put("uid", user.getUid());
            body.put("nickname", user.getNickname());
            body.put("email", user.getEmail());
            searchFeignClient.indexUser(body);
        } catch (Exception e) {
            log.error("ES indexing failed for uid={}: {}", user.getUid(), e.getMessage());
        }
    }
}
