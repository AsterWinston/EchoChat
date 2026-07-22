package me.aster.echochat.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.auth.dto.*;
import me.aster.echochat.auth.service.AuthService;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST 控制器，暴露认证相关端点：注册、登录、登出和设备管理。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册新用户账户。
     *
     * @param req 注册请求
     * @return 包含访问令牌和刷新令牌的令牌响应
     */
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /**
     * 认证用户并颁发令牌。
     *
     * @param req 包含凭证的登录请求
     * @return 令牌响应
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 使用有效的刷新令牌刷新访问令牌和刷新令牌。
     *
     * @param req 包含 refreshToken 的请求
     * @return 新的令牌响应
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.ok(authService.refresh(req.getRefreshToken()));
    }

    /**
     * 将当前用户的访问令牌加入黑名单，实现登出。
     *
     * @param authHeader Authorization 请求头
     * @return 成功结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(BusinessConstants.BEARER_PREFIX_LENGTH);
        Long uid = UserContext.get();
        if (uid == null) {
            return Result.ok();
        }
        authService.logout(uid, token);
        return Result.ok();
    }

    /**
     * 修改当前用户密码并使所有令牌失效。
     *
     * @param req 包含 oldPassword 和 newPassword 的请求
     * @return 成功结果
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long uid = UserContext.get();
        authService.changePassword(uid, req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }

    /**
     * 列出当前用户关联的所有设备。
     *
     * @return 设备信息列表
     */
    @GetMapping("/devices")
    public Result<List<DeviceInfo>> getDevices() {
        Long uid = UserContext.get();
        return Result.ok(authService.getDevices(uid));
    }

    /**
     * 踢出指定设备，使其令牌失效并标记为离线。
     *
     * @param deviceId 要踢出的设备 ID
     * @return 成功结果
     */
    @DeleteMapping("/devices/{deviceId}")
    public Result<Void> kickDevice(@PathVariable String deviceId) {
        Long uid = UserContext.get();
        authService.kickDevice(uid, deviceId);
        return Result.ok();
    }
}
