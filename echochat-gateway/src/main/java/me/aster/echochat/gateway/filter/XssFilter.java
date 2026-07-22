package me.aster.echochat.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 拒绝请求路径、查询参数和指定请求头（Referer、User-Agent）中包含常见XSS（跨站脚本）和SQL注入模式的请求的过滤器。
 * @author AsterWinston
 */
@Slf4j
@Component
public class XssFilter implements GlobalFilter, Ordered {

    /** XSS和SQL注入检测的正则表达式模式列表 */
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)<script\\b"),
            Pattern.compile("(?i)javascript:"),
            Pattern.compile("(?i)\\bonerror\\s*="),
            Pattern.compile("(?i)\\bonload\\s*="),
            Pattern.compile("(?i)<\\s*iframe\\b"),
            Pattern.compile("(?i)(?:'|%27)\\s*(?:or|and)\\s+.*?(?:=|%3D)"),
            Pattern.compile("(?i)\\bunion\\s+select\\b"),
            Pattern.compile("(?i)\\bdrop\\s+table\\b"),
            Pattern.compile("(?i)--\\s*$")
    );

    /**
     * 在请求的路径、查询参数和指定请求头中扫描XSS/SQL注入模式。
     *
     * @param exchange 当前服务器交换对象
     * @param chain    过滤器链
     * @return 过滤完成后完成的Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        String allParams = rawQuery != null ? rawQuery : "";

        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(path).find() || pattern.matcher(allParams).find()) {
                log.warn("XSS/SQL injection pattern detected in request: path={}, query={}", path, rawQuery);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
        }

        String referer = exchange.getRequest().getHeaders().getFirst("Referer");
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        List<String> values = new ArrayList<>();
        if (referer != null) {
            values.add(referer);
        }
        if (userAgent != null) {
            values.add(userAgent);
        }
        for (String val : values) {
            for (Pattern pattern : PATTERNS) {
                if (pattern.matcher(val).find()) {
                    log.warn("XSS/SQL injection pattern detected in header: path={}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                    return exchange.getResponse().setComplete();
                }
            }
        }

        return chain.filter(exchange);
    }

    /**
     * 返回 {@code -250}，确保此过滤器在 {@link IpBlacklistFilter}（-200）之前、
     * {@link HeaderSanitizerFilter}（-300）之后执行，尽早拦截恶意请求。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return -250;
    }
}