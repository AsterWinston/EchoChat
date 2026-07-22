package me.aster.echochat.auth.service;

import me.aster.echochat.auth.dto.DeviceInfo;
import me.aster.echochat.auth.dto.LoginRequest;
import me.aster.echochat.auth.dto.RegisterRequest;
import me.aster.echochat.auth.dto.TokenResponse;
import java.util.List;

/**
 * 认证服务接口，提供用户注册、登录、令牌管理和设备管理功能。
 * @author AsterWinston
 */
public interface AuthService {

    /**
     * 注册新用户并颁发令牌。
     *
     * @param req 注册请求
     * @return 令牌响应
     */
    TokenResponse register(RegisterRequest req);

    /**
     * 使用凭证认证用户并颁发令牌。
     *
     * @param req 登录请求
     * @return 令牌响应
     */
    TokenResponse login(LoginRequest req);

    /**
     * 使用有效的刷新令牌刷新令牌，将旧令牌加入黑名单。
     *
     * @param refreshToken 当前的刷新令牌
     * @return 新的令牌响应
     */
    TokenResponse refresh(String refreshToken);

    /**
     * 将令牌加入黑名单并移除设备状态，实现用户登出。
     *
     * @param uid   用户 ID
     * @param token 要加入黑名单的访问令牌
     */
    void logout(Long uid, String token);

    /**
     * 修改用户密码并使所有活跃令牌失效。
     *
     * @param uid         用户 ID
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     */
    void changePassword(Long uid, String oldPassword, String newPassword);

    /**
     * 获取用户的所有设备及其在线状态。
     *
     * @param uid 用户 ID
     * @return 设备信息列表
     */
    List<DeviceInfo> getDevices(Long uid);

    /**
     * 踢出设备：使其令牌失效并标记为离线。
     *
     * @param uid      用户 ID
     * @param deviceId 要踢出的设备 ID
     */
    void kickDevice(Long uid, String deviceId);
}
