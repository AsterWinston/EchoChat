package me.aster.echochat.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.gateway.constant.GatewayConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 全局过滤器，检查请求IP是否存在于Redis黑名单集合中，如果存在则拒绝请求并返回HTTP 403。
 *
 * <p>IP标准化处理包括IPv6回环地址（{@code 0:0:0:0:0:0:0:1} → {@code 127.0.0.1}）
 * 和IPv4映射的IPv6地址（{@code ::ffff:x.x.x.x} → {@code x.x.x.x}）。
 * 如果Redis不可达，过滤器将放行（失败放通）。
 * @author AsterWinston
 */
@Slf4j
@Component
public class IpBlacklistFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_KEY = "ip:blacklist";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 使用给定的响应式Redis模板构造过滤器。
     *
     * @param redisTemplate 用于查询黑名单集合的响应式Redis客户端
     */
    public IpBlacklistFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 标准化客户端IP并检查其在Redis黑名单集合中的成员关系。
     * 如果IP在黑名单中，则阻止请求并返回HTTP 403。
     *
     * @param exchange 当前服务器交换对象
     * @param chain    网关过滤器链
     * @return 过滤完成后完成的Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = normalizeIp(exchange.getRequest().getRemoteAddress());
        return redisTemplate.opsForSet().isMember(BLACKLIST_KEY, ip)
                .timeout(BusinessConstants.REDIS_TIMEOUT_1S)
                .onErrorResume(e -> {
                    log.warn("IP blacklist check failed - allowing pass: ip={}, error={}", ip, e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(blocked -> {
                    if (Boolean.TRUE.equals(blocked)) {
                        log.warn("Blocked IP: {}", ip);
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * 返回 {@code -200}，确保此过滤器在 {@link RateLimitFilter} 和
     * {@link JwtAuthFilter} 之前执行，尽早拒绝黑名单IP。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return -200;
    }

    /**
     * 标准化客户端IP地址，确保黑名单查询的一致性。
     * 将IPv6回环地址（{@code 0:0:0:0:0:0:0:1}）映射为 {@code 127.0.0.1}，
     * 并剥离 {@code ::ffff:x.x.x.x} 地址的IPv4映射前缀。
     *
     * @param remoteAddress 入站请求的socket地址
     * @return 标准化后的IP字符串，如果地址不存在则返回 {@code "unknown"}
     */
    private String normalizeIp(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return BusinessConstants.UNKNOWN_IP;
        }
        InetAddress addr = remoteAddress.getAddress();
        if (addr.isLoopbackAddress()) {
            return "127.0.0.1";
        }
        String host = addr.getHostAddress();
        if (host != null && host.startsWith(GatewayConstants.IPV4_MAPPED_IPV6_PREFIX)) {
            return host.substring(7);
        }
        return host != null ? host : BusinessConstants.UNKNOWN_IP;
    }
}