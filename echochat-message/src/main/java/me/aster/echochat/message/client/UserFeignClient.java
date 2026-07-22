package me.aster.echochat.message.client;

import me.aster.echochat.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * echochat-user服务的Feign客户端，用于查询用户信息和好友状态。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-user", path = "/internal/user")
public interface UserFeignClient {

    /**
     * 根据用户ID获取用户信息。
     *
     * @param uid 要查询的用户ID
     * @return 给定ID对应的{@link me.aster.echochat.common.entity.User}
     */
    @GetMapping("/{uid}")
    User getUserByUid(@PathVariable("uid") Long uid);

    /**
     * 批量获取用户资料，一次调用完成。
     *
     * @param uids 要获取的用户ID
     * @return 匹配的用户列表
     */
    @PostMapping("/batch")
    List<User> getUsersByUids(@RequestBody List<Long> uids);

    /**
     * 检查两个用户是否为好友关系。
     *
     * @param uid1 第一个用户ID
     * @param uid2 第二个用户ID
     * @return 包含好友检查结果的map，键为"friends"
     */
    @GetMapping("/friend/check")
    Map<String, Boolean> checkFriendship(@RequestParam("uid1") Long uid1, @RequestParam("uid2") Long uid2);

    /**
     * 检查用户是否被拉黑。
     *
     * @param uid        当前用户ID
     * @param blockedUid 被检查的用户ID
     * @return 包含黑名单检查结果的map，键为"blocked"
     */
    @GetMapping("/blacklist/check")
    Map<String, Boolean> checkBlacklist(@RequestParam("uid") Long uid, @RequestParam("blockedUid") Long blockedUid);

    /**
     * 更新用户的最后在线时间。
     *
     * @param uid 用户ID
     */
    @PutMapping("/{uid}/last-seen")
    void updateLastSeen(@PathVariable("uid") Long uid);

    /**
     * 获取好友备注。
     *
     * @param uid       所属用户ID
     * @param friendUid 好友的用户ID
     * @return 包含备注信息的map
     */
    @GetMapping("/{uid}/friend-memo/{friendUid}")
    Map<String, String> getFriendMemo(@PathVariable("uid") Long uid, @PathVariable("friendUid") Long friendUid);

    /**
     * 批量获取好友备注，一次调用完成。
     *
     * @param uid        所属用户ID
     * @param friendUids 要查询的好友UID
     * @return friendUid（字符串）到备注的映射，无备注的条目不包含
     */
    @PostMapping("/{uid}/friend-memos")
    Map<String, String> getFriendMemos(@PathVariable("uid") Long uid, @RequestBody List<Long> friendUids);
}