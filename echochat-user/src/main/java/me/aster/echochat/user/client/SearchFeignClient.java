package me.aster.echochat.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * echochat-search 服务的 Feign 客户端，用于在用户个人资料更新后将用户和群组索引到 Elasticsearch。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-search", path = "/internal/search")
public interface SearchFeignClient {

    /**
     * 将用户索引到 Elasticsearch。
     * @param body 包含用户数据的请求体
     */
    @PostMapping("/index/user")
    void indexUser(@RequestBody Map<String, Object> body);

    /**
     * 将群组索引到 Elasticsearch。
     * @param body 包含群组数据的请求体
     */
    @PostMapping("/index/group")
    void indexGroup(@RequestBody Map<String, Object> body);
}
