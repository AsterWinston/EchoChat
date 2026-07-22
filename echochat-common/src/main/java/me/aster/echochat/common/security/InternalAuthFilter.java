package me.aster.echochat.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 保护 {@code /internal/**} 端点免受直接外部访问。
 * 每个内部（服务间）请求必须在 {@link InternalAuthConstants#INTERNAL_TOKEN_HEADER} 头中携带共享令牌；
 * 令牌由调用方的 InternalFeignConfig 中的 Feign 拦截器自动附加。
 * 当令牌缺失、不匹配或令牌未配置时（默认拒绝），请求将被拒绝并返回403。
 * @author AsterWinston
 */
@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    /** 用于错误响应体的JSON序列化器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 共享内部令牌，从配置中注入。 */
    @Value("${" + InternalAuthConstants.INTERNAL_TOKEN_PROPERTY + ":}")
    private String internalToken;

    /**
     * @param request HTTP请求
     * @return 对于非内部路径返回true，跳过此过滤器
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    /**
     * @param request     HTTP请求
     * @param response    HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException 如果发生servlet错误
     * @throws IOException      如果发生I/O错误
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (internalToken == null || internalToken.isBlank()) {
            log.error("security.internal-token is not configured - rejecting internal request: {}",
                    request.getRequestURI());
            reject(response);
            return;
        }
        String provided = request.getHeader(InternalAuthConstants.INTERNAL_TOKEN_HEADER);
        if (provided == null || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Internal token missing or mismatched: path={}, remote={}",
                    request.getRequestURI(), request.getRemoteAddr());
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 写入403 JSON错误响应。
     *
     * @param response HTTP响应
     * @throws IOException 如果写入失败
     */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.fail(ResultCode.FORBIDDEN)));
    }
}