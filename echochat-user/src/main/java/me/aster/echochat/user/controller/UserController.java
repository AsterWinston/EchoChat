package me.aster.echochat.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.user.dto.UpdateProfileRequest;
import me.aster.echochat.user.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户个人资料和在线状态操作的 REST 控制器。
 * 处理用户个人资料的查看、更新、搜索以及在线状态查询。
 * @author AsterWinston
 */
@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取已认证用户自己的个人资料。
     *
     * @return 用户个人资料
     */
    @GetMapping("/profile")
    public Result<User> getProfile() {
        Long uid = UserContext.get();
        return Result.ok(userService.getProfile(uid));
    }

    /**
     * 更新已认证用户的个人资料字段。
     *
     * @param req 要更新的资料字段，仅应用非空字段
     * @return 更新后的用户个人资料
     */
    @PutMapping("/profile")
    public Result<User> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        Long uid = UserContext.get();
        return Result.ok(userService.updateProfile(uid, req.toUpdatesMap()));
    }

    /**
     * 根据 UID 获取用户的公开个人资料。
     *
     * @param uid 目标用户的唯一 ID
     * @return 不包含 email 的公开个人资料
     */
    @GetMapping("/profile/{uid}")
    public Result<User> getProfileByUid(@PathVariable Long uid) {
        return Result.ok(userService.getProfileByUid(uid));
    }

    /**
     * 根据关键词搜索用户，按 UID、邮箱和昵称匹配。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表，敏感字段已脱敏
     */
    @GetMapping("/search")
    public Result<List<User>> searchUsers(@RequestParam @NotBlank @Size(max = 64) String keyword) {
        UserContext.get();
        return Result.ok(userService.searchUsers(keyword));
    }

    /**
     * 批量查询用户的在线状态。
     *
     * @param uids 用户 ID 列表，最多 500 个
     * @return uid 字符串到在线状态的映射
     */
    @PostMapping("/online-status/batch")
    public Result<Map<String, Boolean>> getBatchOnlineStatus(@RequestBody @Size(max = 500) List<Long> uids) {
        Map<String, Boolean> result = new LinkedHashMap<>(16);
        for (Long uid : uids) {
            result.put(String.valueOf(uid), userService.isUserOnline(uid));
        }
        return Result.ok(result);
    }
}
