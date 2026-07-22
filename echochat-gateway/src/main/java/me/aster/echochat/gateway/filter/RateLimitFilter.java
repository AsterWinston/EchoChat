package me.aster.echochat.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 多层级响应式限流过滤器，通过Redis原子计数器配合滑动窗口过期机制
 * 实施可配置的限流策略。
 *
 * <p>限流层级（按顺序评估）：
 * <ol>
 *   <li><b>全局IP限流</b> —— 限制来自单个IP的所有请求。</li>
 *   <li><b>登录IP限流</b> —— 限制 {@code /api/auth/login} 上的认证尝试。</li>
 *   <li><b>注册IP限流</b> —— 限制 {@code /api/auth/register} 上的注册尝试。</li>
   *   <li><b>消息发送每用户限流</b> —— 限制 {@code /api/message/send} 和 {@code /api/message/send/group} 上的消息发送，
 *       以 {@code X-User-Id} 头为键。</li>
 *   <li><b>消息转发每用户限流</b> —— 限制 {@code /api/message/forward} 上的消息转发，
 *       以 {@code X-User-Id} 头为键。</li>
 * </ol>
 *
 * <p>每个层级使用 Lua 脚本来原子性地递增计数器并设置过期时间，实现滑动窗口计数。
 * 如果Redis不可达，过滤器将放行（失败放通）。
 * @author AsterWinston
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int globalLimit;
    private final int globalWindowSeconds;
    private final int loginLimit;
    private final int loginWindowSeconds;
    private final int messageLimit;
    private final int messageWindowSeconds;

    /** Lua脚本：原子递增计数器并在首次设置时设置过期时间，超过限制则返回0 */
    private static final DefaultRedisScript<Long> RATE_LIMITER_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "if current > tonumber(ARGV[2]) then " +
            "  return 0 " +
            "end " +
            "return 1",
            Long.class);

    /**
     * 使用可配置的限流参数构造过滤器。
     *
     * @param redisTemplate       用于原子计数器操作的响应式Redis客户端
     * @param globalLimit         全局窗口内每个IP的最大请求数
     * @param globalWindowSeconds 全局层级的滑动窗口时长（秒）
     * @param loginLimit          登录窗口内每个IP的最大登录尝试次数
     * @param loginWindowSeconds  登录层级的滑动窗口时长（秒）
     * @param messageLimit        消息窗口内每个用户的最大消息发送数
     * @param messageWindowSeconds 消息层级的滑动窗口时长（秒）
     */
    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                           @Value("${rate-limit.global.limit:100}") int globalLimit,
                           @Value("${rate-limit.global.window:1}") int globalWindowSeconds,
                           @Value("${rate-limit.login.limit:5}") int loginLimit,
                           @Value("${rate-limit.login.window:60}") int loginWindowSeconds,
                           @Value("${rate-limit.message.limit:30}") int messageLimit,
                           @Value("${rate-limit.message.window:1}") int messageWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.globalLimit = globalLimit;
        this.globalWindowSeconds = globalWindowSeconds;
        this.loginLimit = loginLimit;
        this.loginWindowSeconds = loginWindowSeconds;
        this.messageLimit = messageLimit;
        this.messageWindowSeconds = messageWindowSeconds;
    }

    /**
     * 对入站请求应用多层级限流。
     *
     * @param exchange 当前服务器交换对象
     * @param chain    网关过滤器链
     * @return 过滤完成后完成的Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = resolveClientIp(exchange);
        String path = exchange.getRequest().getURI().getPath();
        String globalKey = RedisKeyConstants.RATE_LIMIT_IP_PREFIX + ip;

        return checkLimit(globalKey, globalLimit, globalWindowSeconds)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return tooManyRequests(exchange);
                    }

                    if (path.startsWith("/api/auth/login")) {
                        String loginKey = RedisKeyConstants.RATE_LIMIT_LOGIN_PREFIX + ip;
                        return checkLimit(loginKey, loginLimit, loginWindowSeconds)
                                .flatMap(loginAllowed -> loginAllowed ? chain.filter(exchange) : tooManyRequests(exchange));
                    }

                    if (path.startsWith("/api/auth/register")) {
                        String registerKey = RedisKeyConstants.RATE_LIMIT_REGISTER_PREFIX + ip;
                        return checkLimit(registerKey, 3, 3600)
                                .flatMap(regAllowed -> regAllowed ? chain.filter(exchange) : tooManyRequests(exchange));
                    }

                    if (path.startsWith("/api/message/send") || path.startsWith("/api/message/send/group")) {
                        String uidHeader = exchange.getRequest().getHeaders().getFirst(BusinessConstants.USER_ID_HEADER);
                        if (uidHeader != null) {
                            String msgKey = RedisKeyConstants.RATE_LIMIT_MSG_PREFIX + uidHeader;
                            return checkLimit(msgKey, messageLimit, messageWindowSeconds)
                                    .flatMap(msgAllowed -> msgAllowed ? chain.filter(exchange) : tooManyRequests(exchange));
                        }
                    }

                    if (path.startsWith("/api/message/forward")) {
                        String uidHeader = exchange.getRequest().getHeaders().getFirst(BusinessConstants.USER_ID_HEADER);
                        if (uidHeader != null) {
                            String fwdKey = RedisKeyConstants.RATE_LIMIT_FWD_PREFIX + uidHeader;
                            return checkLimit(fwdKey, 15, 1)
                                    .flatMap(fwdAllowed -> fwdAllowed ? chain.filter(exchange) : tooManyRequests(exchange));
                        }
                    }

                    return chain.filter(exchange);
                });
    }

    /**
     * 使用Lua脚本原子性地递增Redis计数器并与限制值比较。
     * 首次递增时设置键的TTL以实现滑动窗口效果。
     * 如果Redis不可达或超时，则失败放通（返回 {@code true}）。
     *
     * @param key           Redis限流桶的键名
     * @param limit         最大允许次数
     * @param windowSeconds 滑动窗口时长（秒）
     * @return 如果请求被允许则返回 {@code true}，如果被限流则返回 {@code false}
     */
    private Mono<Boolean> checkLimit(String key, int limit, int windowSeconds) {
        return redisTemplate.execute(RATE_LIMITER_SCRIPT,
                        java.util.List.of(key),
                        java.util.List.of(String.valueOf(windowSeconds), String.valueOf(limit)))
                .single()
                .map(result -> result == 1L)
                .timeout(BusinessConstants.REDIS_TIMEOUT_1S)
                .defaultIfEmpty(true)
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed - allowing pass: key={}, error={}", key, e.getMessage());
                    return Mono.just(true);
                });
    }

    /**
     * 解析客户端IP，优先使用 {@code X-Forwarded-For} 头，回退到socket远程地址。
     * 当远程地址不可用时（如在某些代理后面）返回 {@code "unknown"}，
     * 以避免导致所有请求失败的NullPointerException。
     *
     * @param exchange 当前服务器交换对象
     * @return 可用作限流键的非null IP字符串
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return BusinessConstants.UNKNOWN_IP;
    }

    /**
     * 将响应状态码设置为429 Too Many Requests并完成交换。
     *
     * @param exchange 当前服务器交换对象
     * @return 表示响应完成的Mono
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    /**
     * 返回 {@code -90}，确保此过滤器在 {@link IpBlacklistFilter}（-200）和
     * {@link JwtAuthFilter}（-100）之后执行，仅在认证验证完成后才进行限流。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return -90;
    }
}