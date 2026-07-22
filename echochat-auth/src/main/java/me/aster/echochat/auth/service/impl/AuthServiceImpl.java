package me.aster.echochat.auth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.auth.client.UserFeignClient;
import me.aster.echochat.auth.constant.AuthConstants;
import me.aster.echochat.auth.dto.DeviceInfo;
import me.aster.echochat.auth.dto.LoginRequest;
import me.aster.echochat.auth.dto.RegisterRequest;
import me.aster.echochat.auth.dto.TokenResponse;
import me.aster.echochat.auth.service.AuthService;
import me.aster.echochat.auth.util.JwtUtil;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.OnlinePresenceUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证服务实现，处理注册、登录、令牌管理和设备管理。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserFeignClient userFeignClient;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    /** 用于设备信息存储的 JSON 序列化器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public TokenResponse register(RegisterRequest req) {
        Map<String, Object> createReq = new HashMap<>(16);
        createReq.put("email", req.getEmail());
        createReq.put("password", req.getPassword());
        createReq.put("nickname", req.getNickname());

        User user = userFeignClient.createUser(createReq);
        return issueTokens(user, req.getDeviceId(), req.getPlatform());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TokenResponse login(LoginRequest req) {
        Map<String, String> verifyBody = new HashMap<>(16);
        verifyBody.put("account", req.getAccount());
        verifyBody.put("password", req.getPassword());
        Map<String, Object> verifyResult = userFeignClient.verifyPassword(verifyBody);

        boolean valid = (boolean) verifyResult.get("valid");
        if (!valid) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        Object uidObj = verifyResult.get("uid");
        Long uid = uidObj instanceof Number ? ((Number) uidObj).longValue() : Long.parseLong(uidObj.toString());
        User user = userFeignClient.getUserByUid(uid);
        return issueTokens(user, req.getDeviceId(), req.getPlatform());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TokenResponse refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Wrong token type");
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenBlacklistKey(refreshToken)))) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Token revoked");
        }

        Long uid = jwtUtil.getUidFromToken(refreshToken);
        String deviceId = jwtUtil.getDeviceIdFromToken(refreshToken);

        User user = userFeignClient.getUserByUid(uid);

        String newAccessToken = jwtUtil.generateAccessToken(uid, deviceId);
        String newRefreshToken = jwtUtil.generateRefreshToken(uid, deviceId);

        blacklistToken(refreshToken);
        storeToken(uid, deviceId, newAccessToken, newRefreshToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(AuthConstants.ACCESS_TOKEN_EXPIRATION_SECONDS)
                .uid(uid)
                .nickname(user.getNickname())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout(Long uid, String token) {
        String deviceId = jwtUtil.getDeviceIdFromToken(token);
        blacklistToken(token);

        redisTemplate.delete(accessTokenKey(uid, deviceId));
        redisTemplate.delete(refreshTokenKey(uid, deviceId));
        OnlinePresenceUtil.markOffline(redisTemplate, uid, deviceId);
redisTemplate.opsForValue().set(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid, String.valueOf(System.currentTimeMillis()), BusinessConstants.REDIS_DEFAULT_TTL);

        Map<String, String> body = new HashMap<>(16);
        body.put("deviceId", deviceId);
        try {
            userFeignClient.markOffline(uid, body);
        } catch (Exception e) {
            log.warn("markOffline failed: uid={}, deviceId={}", uid, deviceId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(Long uid, String oldPassword, String newPassword) {
        Map<String, String> body = new HashMap<>(16);
        body.put("oldPassword", oldPassword);
        body.put("newPassword", newPassword);
        userFeignClient.changePassword(uid, body);
        invalidateAllTokens(uid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DeviceInfo> getDevices(Long uid) {
        Set<String> onlineDevices = OnlinePresenceUtil.onlineDevices(redisTemplate, uid);

        Map<Object, Object> deviceMap = redisTemplate.opsForHash().entries(deviceHashKey(uid));
        Set<String> finalOnlineDevices = onlineDevices;
        return deviceMap.entrySet().stream().map(e -> {
            Map<String, String> info = parseDeviceInfo((String) e.getValue());
            String deviceId = (String) e.getKey();
            return DeviceInfo.builder()
                    .deviceId(deviceId)
                    .platform(info.getOrDefault("platform", BusinessConstants.UNKNOWN_PLATFORM))
                    .loginAt(parseDateTime(info.get("loginAt")))
                    .online(finalOnlineDevices.contains(deviceId))
                    .current(false)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void kickDevice(Long uid, String deviceId) {
        String accessToken = redisTemplate.opsForValue().get(accessTokenKey(uid, deviceId));
        String refreshToken = redisTemplate.opsForValue().get(refreshTokenKey(uid, deviceId));
        if (accessToken != null) {
            blacklistToken(accessToken);
        }
        if (refreshToken != null) {
            blacklistToken(refreshToken);
        }

        redisTemplate.delete(accessTokenKey(uid, deviceId));
        redisTemplate.delete(refreshTokenKey(uid, deviceId));
        OnlinePresenceUtil.markOffline(redisTemplate, uid, deviceId);
        redisTemplate.opsForValue().set(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid, String.valueOf(System.currentTimeMillis()), BusinessConstants.REDIS_DEFAULT_TTL);
        redisTemplate.opsForHash().delete(deviceHashKey(uid), deviceId);

        Map<String, String> body = new HashMap<>(16);
        body.put("deviceId", deviceId);
        try {
            userFeignClient.markOffline(uid, body);
        } catch (Exception e) {
            log.warn("markOffline failed: uid={}, deviceId={}", uid, deviceId, e);
        }
    }

    /**
     * 为用户生成并存储令牌，返回令牌响应。
     * 此处不更新在线状态：用户仅在其 WebSocket 连接建立后才计为在线（参见消息服务）。
     *
     * @param user     已认证的用户
     * @param deviceId 客户端设备 ID
     * @param platform 客户端平台
     * @return 令牌响应
     */
    private TokenResponse issueTokens(User user, String deviceId, String platform) {
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
        }
        String accessToken = jwtUtil.generateAccessToken(user.getUid(), deviceId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUid(), deviceId);

        storeToken(user.getUid(), deviceId, accessToken, refreshToken);
        storeDeviceInfo(user.getUid(), deviceId, platform);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(AuthConstants.ACCESS_TOKEN_EXPIRATION_SECONDS)
                .uid(user.getUid())
                .nickname(user.getNickname())
                .build();
    }

    /**
     * 将设备元数据（平台、登录时间）持久化到 Redis。
     *
     * @param uid      用户 ID
     * @param deviceId 设备 ID
     * @param platform 平台名称
     */
    private void storeDeviceInfo(Long uid, String deviceId, String platform) {
        Map<String, String> info = new LinkedHashMap<>(16);
        info.put("platform", platform != null ? platform : "web");
        info.put("loginAt", LocalDateTime.now().format(AuthConstants.DATE_TIME_FORMATTER));
        try {
            String key = deviceHashKey(uid);
            redisTemplate.opsForHash().put(key, deviceId, objectMapper.writeValueAsString(info));
            redisTemplate.expire(key, Duration.ofDays(90));
        } catch (JsonProcessingException e) {
            log.error("storeDeviceInfo failed", e);
        }
    }

    /**
     * 将 JSON 字符串解析为设备信息映射。
     *
     * @param json JSON 字符串
     * @return 解析后的映射，解析失败时返回空映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseDeviceInfo(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /**
     * 使用标准格式化器解析日期时间字符串。
     *
     * @param str 日期时间字符串
     * @return 解析后的 LocalDateTime，解析失败时返回 null
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(str, AuthConstants.DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将访问令牌和刷新令牌存入 Redis，设置各自的 TTL。
     *
     * @param uid          用户 ID
     * @param deviceId     设备 ID
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌
     */
    private void storeToken(Long uid, String deviceId, String accessToken, String refreshToken) {
        redisTemplate.opsForValue().set(accessTokenKey(uid, deviceId), accessToken,
                Duration.ofSeconds(AuthConstants.ACCESS_TOKEN_EXPIRATION_SECONDS));
        redisTemplate.opsForValue().set(refreshTokenKey(uid, deviceId), refreshToken,
                Duration.ofSeconds(AuthConstants.REFRESH_TOKEN_EXPIRATION_SECONDS));
    }

    /**
     * 将令牌加入黑名单，TTL 与其剩余有效时间一致。
     *
     * @param token 要加入黑名单的令牌
     */
    private void blacklistToken(String token) {
        try {
            long remaining = jwtUtil.getTokenExpiration(token) - System.currentTimeMillis();
            if (remaining > 0) {
                redisTemplate.opsForValue().set(tokenBlacklistKey(token), "1", Duration.ofMillis(remaining));
            }
        } catch (Exception e) {
            log.warn("Token blacklist failed", e);
        }
    }

    /**
     * 使用户的所有令牌失效：将活跃令牌加入黑名单并清理 Redis 状态。
     * 通过遍历设备信息哈希（登录时填充）来枚举设备，因为在线状态仅反映活跃的 WebSocket 连接。
     *
     * @param uid 用户 ID
     */
    private void invalidateAllTokens(Long uid) {
        Set<Object> deviceIds;
        try {
            deviceIds = redisTemplate.opsForHash().keys(deviceHashKey(uid));
        } catch (Exception e) {
            deviceIds = Collections.emptySet();
        }
        for (Object deviceIdObj : deviceIds) {
            String deviceId = String.valueOf(deviceIdObj);
            String access = redisTemplate.opsForValue().get(accessTokenKey(uid, deviceId));
            String refresh = redisTemplate.opsForValue().get(refreshTokenKey(uid, deviceId));
            if (access != null) {
                blacklistToken(access);
            }
            if (refresh != null) {
                blacklistToken(refresh);
            }
            redisTemplate.delete(accessTokenKey(uid, deviceId));
            redisTemplate.delete(refreshTokenKey(uid, deviceId));
        }
        redisTemplate.opsForValue().set(RedisKeyConstants.USER_LAST_SEEN_PREFIX + uid, String.valueOf(System.currentTimeMillis()), BusinessConstants.REDIS_DEFAULT_TTL);
        redisTemplate.delete(deviceHashKey(uid));
        redisTemplate.delete(onlineKey(uid));
    }

    /**
     * 构建存储用户-设备对的活跃访问令牌的 Redis 键。
     *
     * @param uid      用户 ID
     * @param deviceId 设备标识符
     * @return 格式为 {@code token:access:{uid}:{deviceId}} 的 Redis 键
     */
    private String accessTokenKey(Long uid, String deviceId) {
        return RedisKeyConstants.TOKEN_ACCESS_PREFIX + uid + ":" + deviceId;
    }

    /**
     * 构建存储用户-设备对的活跃刷新令牌的 Redis 键。
     *
     * @param uid      用户 ID
     * @param deviceId 设备标识符
     * @return 格式为 {@code token:refresh:{uid}:{deviceId}} 的 Redis 键
     */
    private String refreshTokenKey(Long uid, String deviceId) {
        return RedisKeyConstants.TOKEN_REFRESH_PREFIX + uid + ":" + deviceId;
    }

    /**
     * 构建跟踪用户当前在线设备的 Redis 集合键。
     *
     * @param uid 用户 ID
     * @return 格式为 {@code user:online:{uid}} 的 Redis 键
     */
    private String onlineKey(Long uid) {
        return RedisKeyConstants.USER_ONLINE_PREFIX + uid;
    }

    /**
     * 构建存储用户设备元数据（平台、登录时间）的 Redis 哈希键。
     *
     * @param uid 用户 ID
     * @return 格式为 {@code user:devices:{uid}} 的 Redis 键
     */
    private String deviceHashKey(Long uid) {
        return RedisKeyConstants.USER_DEVICES_PREFIX + uid;
    }

    /**
     * 使用 SHA-256 哈希生成令牌黑名单的 Redis 键。
     *
     * @param token 原始 JWT 令牌
     * @return 黑名单 Redis 键
     */
    private String tokenBlacklistKey(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return RedisKeyConstants.TOKEN_BLACKLIST_PREFIX + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return RedisKeyConstants.TOKEN_BLACKLIST_PREFIX + token.hashCode();
        }
    }
}
