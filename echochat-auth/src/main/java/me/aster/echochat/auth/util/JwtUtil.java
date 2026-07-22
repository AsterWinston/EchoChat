package me.aster.echochat.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.aster.echochat.auth.constant.AuthConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 令牌生成、解析和验证的工具类。
 * @author AsterWinston
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥，从配置中注入。 */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 生成短期有效的访问令牌。
     *
     * @param uid      用户 ID
     * @param deviceId 设备 ID
     * @return 已签名的 JWT 访问令牌
     */
    public String generateAccessToken(Long uid, String deviceId) {
        return Jwts.builder()
                .claim("uid", uid)
                .claim("device_id", deviceId)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + AuthConstants.ACCESS_TOKEN_EXPIRATION_MILLIS))
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 生成长期有效的刷新令牌。
     *
     * @param uid      用户 ID
     * @param deviceId 设备 ID
     * @return 已签名的 JWT 刷新令牌
     */
    public String generateRefreshToken(Long uid, String deviceId) {
        return Jwts.builder()
                .claim("uid", uid)
                .claim("device_id", deviceId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + AuthConstants.REFRESH_TOKEN_EXPIRATION_MILLIS))
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 解析并验证 JWT 令牌，返回其声明。
     *
     * @param token JWT 令牌
     * @return 解析后的声明
     * @throws io.jsonwebtoken.JwtException 如果令牌无效
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证令牌是否格式正确且具有有效签名。
     *
     * @param token JWT 令牌
     * @return 如果令牌有效则返回 true
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从令牌中提取用户 ID。
     *
     * @param token JWT 令牌
     * @return 用户 ID
     */
    public Long getUidFromToken(String token) {
        return parseToken(token).get("uid", Long.class);
    }

    /**
     * 从令牌中提取设备 ID。
     *
     * @param token JWT 令牌
     * @return 设备 ID
     */
    public String getDeviceIdFromToken(String token) {
        return parseToken(token).get("device_id", String.class);
    }

    /**
     * 从令牌中提取令牌类型（access 或 refresh）。
     *
     * @param token JWT 令牌
     * @return 令牌类型
     */
    public String getTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    /**
     * 获取令牌的过期时间戳（毫秒纪元）。
     *
     * @param token JWT 令牌
     * @return 过期时间戳，单位为毫秒
     */
    public long getTokenExpiration(String token) {
        return parseToken(token).getExpiration().getTime();
    }

    /**
     * 从 Base64 编码的密钥派生签名密钥。
     *
     * @return HMAC 签名密钥
     */
    private SecretKey getSignKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
