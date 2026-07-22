package me.aster.echochat.moment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * echochat-user服务的Feign客户端，用于查询好友列表（动态时间线分发）、查询黑名单（隐私过滤）以及验证双向好友关系（点赞/评论权限）。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-user", path = "/internal/user")
public interface UserFeignClient {

    /**
     * @param uid 用户ID
     * @return 好友UID列表
     */
    @GetMapping("/{uid}/friend-uids")
    List<Long> getFriendUids(@PathVariable("uid") Long uid);

    @GetMapping("/{uid}/blacklist-uids")
    List<Long> getBlacklistUids(@PathVariable("uid") Long uid);

    /**
     * 检查两个用户是否为双向好友。
     *
     * @param uid1 第一个用户ID
     * @param uid2 第二个用户ID
     * @return 包含键{@code friends}的映射，表示好友关系状态
     */
    @GetMapping("/friend/check")
    Map<String, Boolean> checkFriendship(@RequestParam("uid1") Long uid1, @RequestParam("uid2") Long uid2);
}
