package me.aster.echochat.user.service;

import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;

import java.util.List;
import java.util.Map;

/**
 * 用户账户和个人资料管理的服务接口。
 * @author AsterWinston
 */
public interface UserService {

    /**
     * 创建新用户账户。
     *
     * @param email    用户邮箱
     * @param password 原始密码
     * @param nickname 显示昵称
     * @return 新创建的用户，密码字段已脱敏
     */
    User createUser(String email, String password, String nickname);

    /**
     * 根据账户标识（UID 或邮箱）查找用户。
     *
     * @param account 账户标识
     * @return 匹配的用户
     * @throws BusinessException 如果用户不存在或账户已注销
     */
    User findByAccount(String account);

    /**
     * 根据用户唯一 ID 获取用户信息。
     *
     * @param uid 用户 ID
     * @return 密码已脱敏的用户
     * @throws BusinessException 如果用户不存在或已注销
     */
    User getUserByUid(Long uid);

    /**
     * 批量获取用户信息，密码已脱敏。已注销或不存在的用户会被静默跳过。
     *
     * @param uids 用户 ID 列表
     * @return 匹配的用户列表
     */
    List<User> getUsersByUids(List<Long> uids);

    /**
     * 更新用户的个人资料字段。
     *
     * @param uid     用户 ID
     * @param updates 字段名到新值的映射
     * @return 更新后的用户
     */
    User updateUserProfile(Long uid, Map<String, Object> updates);

    /**
     * 验证原始密码是否与存储的哈希值匹配。
     *
     * @param account  账户标识
     * @param password 要验证的原始密码
     * @return 如果密码匹配则返回 true
     */
    boolean verifyPassword(String account, String password);

    /**
     * 验证旧密码后修改用户密码。
     *
     * @param uid         用户 ID
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     * @throws BusinessException 如果用户不存在或旧密码不正确
     */
    void changePassword(Long uid, String oldPassword, String newPassword);

    /**
     * 根据关键词搜索用户，敏感字段已脱敏处理。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表，password 和 email 已置空
     */
    List<User> searchUsers(String keyword);

    /**
     * 获取已验证用户自己的个人资料。
     *
     * @param uid 用户 ID
     * @return 完整用户资料
     */
    User getProfile(Long uid);

    /**
     * 更新已验证用户自己的个人资料。
     *
     * @param uid     用户 ID
     * @param updates 字段名到新值的映射
     * @return 更新后的用户
     */
    User updateProfile(Long uid, Map<String, Object> updates);

    /**
     * 获取用户的公开个人资料，不含敏感字段。
     *
     * @param uid 用户 ID
     * @return email 已脱敏的公开个人资料
     */
    User getProfileByUid(Long uid);

    /**
     * 检查用户当前是否在 Redis 中处于在线状态。
     *
     * @param uid 用户 ID
     * @return 如果至少有一个设备在线则返回 true
     */
    boolean isUserOnline(Long uid);

    /**
     * 使用单次 Redis 管道批量检查多个用户的在线状态，避免每用户一次往返。
     *
     * @param uids 要检查的用户 ID 列表
     * @return uid 到在线状态（至少有一个设备在线则为 true）的映射
     */
    java.util.Map<Long, Boolean> getOnlineStatuses(java.util.Collection<Long> uids);

    /**
     * 在 Redis 中标记用户在某设备上上线。
     *
     * @param uid      用户 ID
     * @param deviceId 设备标识符
     */
    void markOnline(Long uid, String deviceId);

    /**
     * 在 Redis 中标记用户在某设备上离线。
     *
     * @param uid      用户 ID
     * @param deviceId 设备标识符
     */
    void markOffline(Long uid, String deviceId);

    /**
     * 从 Redis 中获取用户的最后在线时间戳。
     *
     * @param uid 用户 ID
     * @return 最后在线的毫秒纪元，若从未记录则返回 null
     */
    Long getLastSeen(Long uid);

    /**
     * 更新用户的最后在线时间到数据库。
     *
     * @param uid 用户 ID
     */
    void updateLastSeen(Long uid);
}
