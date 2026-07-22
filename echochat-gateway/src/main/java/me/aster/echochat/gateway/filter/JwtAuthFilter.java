package me.aster.echochat.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.gateway.constant.GatewayConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 全局网关过滤器，在受保护路由上验证JWT访问令牌。
 * @author AsterWinston
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 用于错误响应体的JSON序列化器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ReactiveStringRedisTemplate redisTemplate;

    /** JWT签名密钥，从配置中注入。 */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 使用给定的响应式Redis模板构造过滤器。
     *
     * @param redisTemplate 用于令牌黑名单查询的响应式Redis客户端
     */
    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 验证JWT令牌、检查黑名单并传播用户头信息。
     *
     * @param exchange 当前服务器交换对象
     * @param chain    过滤器链
     * @return 过滤完成后完成的Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isExcluded(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(GatewayConstants.BEARER_PREFIX)) {
            return writeResult(exchange, HttpStatus.UNAUTHORIZED,
                    Result.fail(401, "Not logged in or token expired"));
        }

        String token = authHeader.substring(BusinessConstants.BEARER_PREFIX_LENGTH);

        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return writeResult(exchange, HttpStatus.UNAUTHORIZED,
                    Result.fail(401, "Token invalid or expired"));
        }

        String tokenType = claims.get("type", String.class);
        if (!GatewayConstants.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            return writeResult(exchange, HttpStatus.UNAUTHORIZED,
                    Result.fail(401, "Wrong token type"));
        }

        String blacklistKey = tokenBlacklistKey(token);
        return redisTemplate.opsForValue().get(blacklistKey)
                .timeout(BusinessConstants.REDIS_TIMEOUT_1S)
                .onErrorResume(e -> {
                    log.error("Token blacklist check failed - rejecting: error={}", e.getMessage());
                    return Mono.just(GatewayConstants.BLACKLIST_ERROR_TOKEN);
                })
                .defaultIfEmpty("")
                .flatMap(blacklisted -> {
                    if (GatewayConstants.BLACKLIST_ERROR_TOKEN.equals(blacklisted)) {
                        return writeResult(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                Result.fail(503, "Service temporarily unavailable"));
                    }
                    if (!blacklisted.isEmpty()) {
                        return writeResult(exchange, HttpStatus.UNAUTHORIZED,
                                Result.fail(401, "Token revoked, please login again"));
                    }
                    Long uid = claims.get("uid", Long.class);
                    String deviceId = claims.get("device_id", String.class);

                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .header(BusinessConstants.USER_ID_HEADER, String.valueOf(uid))
                            .header("X-Device-Id", deviceId != null ? deviceId : "")
                            .build();

                    return chain.filter(exchange.mutate().request(request).build());
                });
    }

    /**
     * 检查请求路径是否免于认证。
     *
     * @param path 请求路径
     * @return 如果路径免于认证则返回true
     */
    private boolean isExcluded(String path) {
        return GatewayConstants.EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 向响应写入JSON错误结果。
     *
     * @param exchange 当前服务器交换对象
     * @param status   要设置的HTTP状态码
     * @param result   要序列化的结果体
     * @return 响应写入完成后完成的Mono
     */
    private Mono<Void> writeResult(ServerWebExchange exchange, HttpStatus status, Result<?> result) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(result);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to serialize result", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 使用SHA-256哈希生成令牌黑名单的Redis键，截取前32个十六进制字符作为键后缀。
     *
     * @param token 原始JWT令牌
     * @return 黑名单Redis键
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

    /**
     * 返回 {@code -100}，确保此过滤器在 {@link IpBlacklistFilter}（-200）之后、
     * 但在 {@link RateLimitFilter}（-90）之前执行，从而在限流之前完成认证验证。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return -100;
    }
}