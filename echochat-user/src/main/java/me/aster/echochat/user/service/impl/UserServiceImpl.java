package me.aster.echochat.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.OnlinePresenceUtil;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.user.mapper.UserMapper;
import me.aster.echochat.user.service.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户账户和个人资料管理的服务实现。
 * 处理用户创建、个人资料更新、密码修改以及通过 Redis 跟踪在线状态。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    /** 用于跟踪用户按设备在线状态的 Redis 模板。 */
    private final StringRedisTemplate redisTemplate;

    private static final String FIELD_NICKNAME = "nickname";
    private static final String FIELD_AVATAR = "avatar";
    private static final String FIELD_SIGNATURE = "signature";
    private static final String FIELD_GENDER = "gender";
    private static final String FIELD_AGE = "age";
    private static final String FIELD_EMAIL = "email";
    private static final String PLACEHOLDER_HASH = "PLACEHOLDER";
    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 检查邮箱唯一性后创建新用户。
     * 生成雪花 ID 作为 UID 并使用 BCrypt 哈希密码。
     *
     * @param email    用户邮箱（可为 null）
     * @param password 要哈希的原始密码
     * @param nickname 显示名称，为 null 时默认为 "UserXXXX"
     * @return 新创建的用户，密码已脱敏
     * @throws BusinessException 如果邮箱已被注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String email, String password, String nickname) {
        if (email != null && userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "Email already registered");
        }

        User user = new User();
        user.setUid(idGenerator.nextId());
        user.setNickname(nickname != null ? nickname : "User" + user.getUid() % 10000);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);

        user.setPassword(null);
        return user;
    }

    /**
     * 根据账户标识（UID 或邮箱）查找用户。
     *
     * @param account 账户标识
     * @return 匹配的用户
     * @throws BusinessException 如果用户不存在或账户已注销
     */
    @Override
    public User findByAccount(String account) {
        User user = userMapper.findByAccount(account);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DELETED);
        }
        return user;
    }

    /**
     * 根据 ID 获取用户，响应中密码已脱敏。
     *
     * @param uid 用户 ID
     * @return 密码已置空为 null 的用户
     * @throws BusinessException 如果用户不存在或已注销
     */
    @Override
    public User getUserByUid(Long uid) {
        User user = userMapper.selectById(uid);
        if (isUserDeleted(user)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setPassword(null);
        return user;
    }

    /**
     * 批量获取用户信息，单次查询，密码脱敏并跳过已注销或不存在的用户。
     *
     * @param uids 用户 ID 列表
     * @return 匹配的用户列表
     */
    @Override
    public List<User> getUsersByUids(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(uids).stream()
                .filter(user -> !isUserDeleted(user))
                .peek(user -> user.setPassword(null))
                .toList();
    }

    /**
     * 更新用户个人资料字段，仅更新映射中存在的字段。
     *
     * @param uid     用户 ID
     * @param updates 字段名到新值的映射（nickname、avatar、signature、gender、age、email）
     * @return 更新后的用户
     */
    @Override
    public User updateUserProfile(Long uid, Map<String, Object> updates) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getUid, uid);
        applyUpdate(wrapper, User::getNickname, updates, FIELD_NICKNAME);
        applyUpdate(wrapper, User::getAvatar, updates, FIELD_AVATAR);
        applyUpdate(wrapper, User::getSignature, updates, FIELD_SIGNATURE);
        applyUpdate(wrapper, User::getGender, updates, FIELD_GENDER);
        applyUpdate(wrapper, User::getAge, updates, FIELD_AGE);
        applyUpdate(wrapper, User::getEmail, updates, FIELD_EMAIL);
        userMapper.update(wrapper);
        return getUserByUid(uid);
    }

    /**
     * 验证旧密码后修改用户密码。
     *
     * @param uid         用户 ID
     * @param oldPassword 用于验证的当前原始密码
     * @param newPassword 要加密并存储的新原始密码
     * @throws BusinessException 如果用户不存在或旧密码不正确
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long uid, String oldPassword, String newPassword) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!checkPassword(oldPassword, user)) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "Incorrect old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    /**
     * 验证原始密码是否与指定账户的存储哈希值匹配。
     *
     * @param account  账户标识（UID 或邮箱）
     * @param password 要验证的原始密码
     * @return 如果密码有效则返回 true
     */
    @Override
    public boolean verifyPassword(String account, String password) {
        User user = findByAccount(account);
        return checkPassword(password, user);
    }

    /**
     * 根据关键词搜索未删除的用户，敏感数据已脱敏。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表，password 和 email 已置空
     */
    @Override
    public List<User> searchUsers(String keyword) {
        List<User> users = userMapper.searchByKeyword(keyword);
        users.forEach(u -> {
            u.setPassword(null);
            u.setEmail(null);
        });
        return users;
    }

    /**
     * 获取用户自己的完整个人资料。
     *
     * @param uid 用户 ID
     * @return 用户个人资料
     */
    @Override
    public User getProfile(Long uid) {
        return getUserByUid(uid);
    }

    /**
     * 更新用户自己的个人资料字段。
     *
     * @param uid     用户 ID
     * @param updates 字段名到新值的映射
     * @return 更新后的用户
     */
    @Override
    public User updateProfile(Long uid, Map<String, Object> updates) {
        return updateUserProfile(uid, updates);
    }

    /**
     * 获取公开用户资料，邮箱已脱敏。
     *
     * @param uid 用户 ID
     * @return 公开个人资料
     */
    @Override
    public User getProfileByUid(Long uid) {
        User user = getUserByUid(uid);
        user.setEmail(null);
        return user;
    }

    /**
     * 检查用户是否在线：至少有一个设备的心跳在存活窗口内（参见 {@link OnlinePresenceUtil}）。
     *
     * @param uid 用户 ID
     * @return 如果至少有一个设备注册为在线则返回 true
     */
    @Override
    public boolean isUserOnline(Long uid) {
        return OnlinePresenceUtil.isOnline(redisTemplate, uid);
    }

    /**
     * 使用单次 Redis 管道批量检查多个用户的在线状态。
     *
     * @param uids 要检查的用户 ID 列表
     * @return uid 到在线状态的映射
     */
    @Override
    public Map<Long, Boolean> getOnlineStatuses(java.util.Collection<Long> uids) {
        Map<Long, Boolean> result = new java.util.LinkedHashMap<>();
        if (uids == null || uids.isEmpty()) {
            return result;
        }
        List<Long> uidList = new java.util.ArrayList<>(uids);
        long cutoff = OnlinePresenceUtil.windowCutoff();
        List<Object> counts = redisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    org.springframework.data.redis.connection.StringRedisConnection src =
                            (org.springframework.data.redis.connection.StringRedisConnection) connection;
                    for (Long uid : uidList) {
                        src.zCount(OnlinePresenceUtil.key(uid), cutoff, Double.MAX_VALUE);
                    }
                    return null;
                });
        for (int i = 0; i < uidList.size(); i++) {
            Object count = i < counts.size() ? counts.get(i) : null;
            boolean online = count instanceof Number && ((Number) count).longValue() > 0;
            result.put(uidList.get(i), online);
        }
        return result;
    }

    /**
     * 在用户的在线状态 ZSET 中注册设备心跳。
     *
     * @param uid      用户 ID
     * @param deviceId 要注册的设备标识符
     */
    @Override
    public void markOnline(Long uid, String deviceId) {
        OnlinePresenceUtil.markOnline(redisTemplate, uid, deviceId);
    }

    /**
     * 将设备从用户的在线状态 ZSET 中移除，并在 Redis 中记录最后在线时间戳。
     *
     * @param uid      用户 ID
     * @param deviceId 要取消注册的设备标识符
     */
    @Override
    public void markOffline(Long uid, String deviceId) {
        OnlinePresenceUtil.markOffline(redisTemplate, uid, deviceId);
        redisTemplate.opsForValue().set(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid, String.valueOf(System.currentTimeMillis()), BusinessConstants.REDIS_DEFAULT_TTL);
    }

    /**
     * 从 Redis 键 {@code user:last_seen:{uid}} 获取用户的最后在线时间戳。
     *
     * @param uid 用户 ID
     * @return 最后在线的毫秒纪元，若从未记录则返回 null
     */
    @Override
    public Long getLastSeen(Long uid) {
        String timestamp = redisTemplate.opsForValue().get(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid);
        return timestamp != null ? Long.parseLong(timestamp) : null;
    }

    /**
     * 将用户的最后在线时间更新到数据库。
     *
     * @param uid 用户 ID
     */
    @Override
    public void updateLastSeen(Long uid) {
        User user = userMapper.selectById(uid);
        if (user != null) {
            user.setLastSeen(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    /**
     * 比较原始密码与存储的哈希值。支持占位密码迁移。
     *
     * @param rawPassword 原始密码输入
     * @param user        包含存储哈希值的用户实体
     * @return 如果密码匹配则返回 true
     */
    private boolean checkPassword(String rawPassword, User user) {
        String hash = user.getPassword();
        if (hash == null) {
            return false;
        }
        if (hash.contains(PLACEHOLDER_HASH)) {
            if (DEFAULT_PASSWORD.equals(rawPassword)) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userMapper.updateById(user);
                return true;
            }
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static boolean isUserDeleted(User user) {
        return user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1);
    }

    private void applyUpdate(LambdaUpdateWrapper<User> wrapper, java.util.function.BiConsumer<LambdaUpdateWrapper<User>, Object> setter, Map<String, Object> updates, String fieldName) {
        if (updates.containsKey(fieldName)) {
            setter.accept(wrapper, updates.get(fieldName));
        }
    }

    private void applyUpdate(LambdaUpdateWrapper<User> wrapper, com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, ?> column, Map<String, Object> updates, String fieldName) {
        if (updates.containsKey(fieldName)) {
            wrapper.set(column, updates.get(fieldName));
        }
    }
}
