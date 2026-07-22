package me.aster.echochat.user.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.user.service.FriendService;
import me.aster.echochat.user.service.UserService;
import me.aster.echochat.user.mq.UserIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内部 REST 控制器，提供服务间用户操作接口。
 * 供网关/认证服务使用，用于创建用户、验证密码和管理在线状态。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final FriendService friendService;
    private final UserIndexService userIndexService;

    /**
     * 根据注册数据创建新用户账户。
     *
     * @param req 包含 email、password 和 nickname 的请求体
     * @return 新创建的用户
     */
    @PostMapping
    public User createUser(@RequestBody Map<String, Object> req) {
        String email = (String) req.get("email");
        String password = (String) req.get("password");
        String nickname = (String) req.get("nickname");
        User user = userService.createUser(email, password, nickname);
        userIndexService.syncUserToEs(user);
        return user;
    }

    /**
     * 根据账户标识（UID 或邮箱）查找用户。
     *
     * @param account 账户标识
     * @return 匹配的用户
     */
    @GetMapping("/account/{account}")
    public User findByAccount(@PathVariable String account) {
        return userService.findByAccount(account);
    }

    /**
     * 验证用户密码，用于认证。
     *
     * @param body 包含 account 和 password 的请求体
     * @return 包含验证结果 valid 和 uid 的映射
     */
    @PostMapping("/account/verify")
    public Map<String, Object> verifyPassword(@RequestBody Map<String, String> body) {
        String account = body.get("account");
        String password = body.get("password");
        boolean valid = userService.verifyPassword(account, password);
        User user = userService.findByAccount(account);
        return Map.of("valid", valid, "uid", user.getUid());
    }

    /**
     * 根据用户唯一 ID 获取用户信息。
     *
     * @param uid 用户 ID
     * @return 用户实体
     */
    @GetMapping("/{uid}")
    public User getUserByUid(@PathVariable Long uid) {
        return userService.getUserByUid(uid);
    }

    /**
     * 更新用户个人资料字段。
     *
     * @param uid     用户 ID
     * @param updates 字段名到新值的映射
     * @return 更新后的用户
     */
    @PutMapping("/{uid}")
    public User updateUserProfile(@PathVariable Long uid, @RequestBody Map<String, Object> updates) {
        User user = userService.updateUserProfile(uid, updates);
        userIndexService.syncUserToEs(user);
        return user;
    }

    /**
     * 验证旧密码后修改用户密码。
     *
     * @param uid  用户 ID
     * @param body 包含 oldPassword 和 newPassword 的请求体
     */
    @PutMapping("/{uid}/password")
    public void changePassword(@PathVariable Long uid, @RequestBody Map<String, String> body) {
        userService.changePassword(uid, body.get("oldPassword"), body.get("newPassword"));
    }

    /**
     * 根据关键词搜索用户。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表
     */
    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam String keyword) {
        return userService.searchUsers(keyword);
    }

    /**
     * 检查用户当前是否在线。
     *
     * @param uid 用户 ID
     * @return 包含 online 状态的映射
     */
    @GetMapping("/status/{uid}")
    public Map<String, Boolean> getOnlineStatus(@PathVariable Long uid) {
        return Map.of("online", userService.isUserOnline(uid));
    }

    /**
     * 批量查询多个用户的在线状态。
     *
     * @param uids 用户 ID 列表
     * @return uid 到 online 状态的映射
     */
    @PostMapping("/status/batch")
    public Map<String, Boolean> getBatchOnlineStatus(@RequestBody List<Long> uids) {
        Map<String, Boolean> result = new java.util.LinkedHashMap<>();
        Map<Long, Boolean> statuses = userService.getOnlineStatuses(uids);
        for (Long uid : uids) {
            result.put(String.valueOf(uid), Boolean.TRUE.equals(statuses.get(uid)));
        }
        return result;
    }

    /**
     * 标记用户在指定设备上上线。
     *
     * @param uid  用户 ID
     * @param body 包含 deviceId 的请求体
     */
    @PostMapping("/online/{uid}")
    public void markOnline(@PathVariable Long uid, @RequestBody Map<String, String> body) {
        userService.markOnline(uid, body.get("deviceId"));
    }

    /**
     * 标记用户在指定设备上离线。
     *
     * @param uid  用户 ID
     * @param body 包含 deviceId 的请求体
     */
    @PostMapping("/offline/{uid}")
    public void markOffline(@PathVariable Long uid, @RequestBody Map<String, String> body) {
        userService.markOffline(uid, body.get("deviceId"));
    }

    /**
     * 返回用户的最后在线时间戳。
     *
     * @param uid 用户 ID
     * @return 包含 lastSeen 毫秒纪元的映射
     */
    @GetMapping("/{uid}/last-seen")
    public Map<String, Object> getLastSeen(@PathVariable Long uid) {
        Long lastSeen = userService.getLastSeen(uid);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("uid", uid);
        result.put("lastSeen", lastSeen);
        return result;
    }

    /**
     * 更新用户的最后在线时间。
     *
     * @param uid 用户 ID
     */
    @PutMapping("/{uid}/last-seen")
    public void updateLastSeen(@PathVariable Long uid) {
        userService.updateLastSeen(uid);
    }

    /**
     * 检查两个用户是否为好友。
     *
     * @param uid1 第一个用户 ID
     * @param uid2 第二个用户 ID
     * @return 包含 friends 状态的映射
     */
    @GetMapping("/friend/check")
    public Map<String, Boolean> checkFriendship(@RequestParam Long uid1, @RequestParam Long uid2) {
        return Map.of("friends", friendService.areFriends(uid1, uid2));
    }

    /**
     * 检查用户是否在目标用户的黑名单中。
     *
     * @param uid        用户 ID
     * @param blockedUid 被检查是否被屏蔽的用户 ID
     * @return 包含 blocked 状态的映射
     */
    @GetMapping("/blacklist/check")
    public Map<String, Boolean> checkBlacklist(@RequestParam Long uid, @RequestParam Long blockedUid) {
        return Map.of("blocked", friendService.isBlacklisted(uid, blockedUid));
    }

    /**
     * 获取用户的所有好友 UID。
     *
     * @param uid 用户 ID
     * @return 好友 UID 列表
     */
    @GetMapping("/{uid}/friend-uids")
    public List<Long> getFriendUids(@PathVariable Long uid) {
        return friendService.getFriendUids(uid);
    }

    /**
     * 获取用户黑名单中的所有用户 UID。
     *
     * @param uid 用户 ID
     * @return 黑名单 UID 列表
     */
    @GetMapping("/{uid}/blacklist-uids")
    public List<Long> getBlacklistUids(@PathVariable Long uid) {
        return friendService.getBlacklistUids(uid);
    }

    /**
     * 获取用户对某个好友的备注。
     *
     * @param uid       用户 ID
     * @param friendUid 好友 UID
     * @return 包含 memo 的映射
     */
    @GetMapping("/{uid}/friend-memo/{friendUid}")
    public Map<String, String> getFriendMemo(@PathVariable Long uid, @PathVariable Long friendUid) {
        String memo = friendService.getFriendMemo(uid, friendUid);
        return Map.of("memo", memo != null ? memo : "");
    }

    /**
     * 批量获取用户个人资料，单次查询，用于会话列表避免 N+1 Feign 调用。
     *
     * @param uids 要获取的用户 ID 列表
     * @return 匹配的用户列表（不存在的 UID 会被静默跳过）
     */
    @PostMapping("/batch")
    public List<User> getUsersByUids(@RequestBody List<Long> uids) {
        return userService.getUsersByUids(uids);
    }

    /**
     * 批量获取好友备注，单次查询，用于会话列表避免 N+1 Feign 调用。
     *
     * @param uid        拥有者用户 ID
     * @param friendUids 要查询的好友 UID 列表
     * @return friendUid（作为字符串）到 memo 的映射，没有备注的条目会被省略
     */
    @PostMapping("/{uid}/friend-memos")
    public Map<String, String> getFriendMemos(@PathVariable Long uid, @RequestBody List<Long> friendUids) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        friendService.getFriendMemos(uid, friendUids)
                .forEach((friendUid, memo) -> result.put(String.valueOf(friendUid), memo));
        return result;
    }
}
