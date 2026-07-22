package me.aster.echochat.group.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * echochat-search服务的Feign客户端，用于在用户资料或群组变更后将用户和群组索引到Elasticsearch。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-search", path = "/internal/search")
public interface SearchFeignClient {

    @PostMapping("/index/user")
    void indexUser(@RequestBody Map<String, Object> body);

    @PostMapping("/index/group")
    void indexGroup(@RequestBody Map<String, Object> body);
}
