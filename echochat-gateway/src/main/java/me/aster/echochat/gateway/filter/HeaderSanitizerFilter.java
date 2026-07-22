package me.aster.echochat.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.gateway.constant.GatewayConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 最先执行的网关过滤器，加固信任边界：
 * <ul>
 *   <li>拒绝任何路径包含 {@code /internal/} 端点的请求 ——
 *       这些是严格的服务间请求，绝不应通过公共网关访问。</li>
 *   <li>从入站请求中剥离身份头（{@code X-User-Id}、{@code X-Device-Id}、
 *       {@code X-Internal-Token}），防止客户端冒充用户或服务；
 *       {@link JwtAuthFilter} 在令牌验证后会重新注入可信值。</li>
 * </ul>
 * @author AsterWinston
 */
@Slf4j
@Component
public class HeaderSanitizerFilter implements GlobalFilter, Ordered {

    /** 用于错误响应体的JSON序列化器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 只能由网关自身设置的身份头。 */
    private static final String[] RESERVED_HEADERS = {BusinessConstants.USER_ID_HEADER, "X-Device-Id", "X-Internal-Token"};

    /**
     * 拦截包含内部路径的请求，并剥离请求中预留的身份头。
     *
     * @param exchange 当前服务器交换对象
     * @param chain    过滤器链
     * @return 过滤完成后完成的Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.contains(GatewayConstants.INTERNAL_PATH_PREFIX)) {
            log.warn("Blocked external access to internal path: {} from {}",
                    path, exchange.getRequest().getRemoteAddress());
            return writeResult(exchange, HttpStatus.FORBIDDEN, Result.fail(403, "Forbidden"));
        }

        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    for (String header : RESERVED_HEADERS) {
                        headers.remove(header);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
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
     * 返回 {@code -300}，确保此过滤器在 {@link IpBlacklistFilter}（-200）之前执行，
     * 从而在任何其他逻辑之前完成头部剥离处理。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return -300;
    }
}