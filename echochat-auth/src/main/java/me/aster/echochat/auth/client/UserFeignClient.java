package me.aster.echochat.auth.client;

import me.aster.echochat.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户服务的 Feign 客户端，提供内部用户操作接口。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-user", path = "/internal/user")
public interface UserFeignClient {

    /**
     * 创建新用户账户。
     *
     * @param request 用户创建数据
     * @return 创建的用户
     */
    @PostMapping
    User createUser(@RequestBody Map<String, Object> request);

    /**
     * 根据账户标识（UID 或邮箱）查找用户。
     *
     * @param account 账户标识
     * @return 匹配的用户
     */
    @GetMapping("/account/{account}")
    User findByAccount(@PathVariable("account") String account);

    /**
     * 验证账户凭证。
     *
     * @param body 验证数据（account 和 password）
     * @return 验证结果，包含 valid 和 uid
     */
    @PostMapping("/account/verify")
    Map<String, Object> verifyPassword(@RequestBody Map<String, String> body);

    /**
     * 根据用户唯一 ID 获取用户信息。
     *
     * @param uid 用户 ID
     * @return 匹配的用户
     */
    @GetMapping("/{uid}")
    User getUserByUid(@PathVariable("uid") Long uid);

    /**
     * 更新用户个人资料。
     *
     * @param uid     用户 ID
     * @param updates 要更新的资料字段
     * @return 更新后的用户
     */
    @PutMapping("/{uid}")
    User updateUserProfile(@PathVariable("uid") Long uid, @RequestBody Map<String, Object> updates);

    /**
     * 修改用户密码。
     *
     * @param uid  用户 ID
     * @param body 旧密码和新密码
     */
    @PutMapping("/{uid}/password")
    void changePassword(@PathVariable("uid") Long uid, @RequestBody Map<String, String> body);

    /**
     * 根据关键词搜索用户（按昵称或账户）。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表
     */
    @GetMapping("/search")
    List<User> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 获取用户的在线状态。
     *
     * @param uid 用户 ID
     * @return 在线状态映射
     */
    @GetMapping("/status/{uid}")
    Map<String, Boolean> getOnlineStatus(@PathVariable("uid") Long uid);

    /**
     * 标记用户在某设备上上线。
     *
     * @param uid  用户 ID
     * @param body 设备信息
     */
    @PostMapping("/online/{uid}")
    void markOnline(@PathVariable("uid") Long uid, @RequestBody Map<String, String> body);

    /**
     * 标记用户在某设备上离线。
     *
     * @param uid  用户 ID
     * @param body 设备信息
     */
    @PostMapping("/offline/{uid}")
    void markOffline(@PathVariable("uid") Long uid, @RequestBody Map<String, String> body);
}
