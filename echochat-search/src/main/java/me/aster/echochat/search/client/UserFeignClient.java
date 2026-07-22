package me.aster.echochat.search.client;

import me.aster.echochat.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * echochat-user服务的Feign客户端，用于在Elasticsearch的user_index为空或无匹配结果时，
 * 通过MySQL回退搜索用户。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-user", path = "/internal/user")
public interface UserFeignClient {

    /**
     * 根据关键词搜索用户。
     *
     * @param keyword 用于匹配用户资料的搜索关键词
     * @return 匹配的{@link User}记录列表
     */
    @GetMapping("/search")
    List<User> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 根据UID列表批量查询用户。
     *
     * @param uids 用户ID列表
     * @return 匹配的用户列表
     */
    @PostMapping("/batch")
    List<User> getUsersByUids(@RequestBody List<Long> uids);
}
