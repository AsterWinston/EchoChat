package me.aster.echochat.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.context.UserContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet 过滤器，提取 X-User-Id 请求头并存入线程局部 UserContext。
 * 每次请求后清除上下文，防止泄漏。
 * @author AsterWinston
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 读取 X-User-Id 请求头，设置 UserContext，并在过滤器链完成后清除。
     *
     * @param request     传入的 HTTP 请求
     * @param response    传出的 HTTP 响应
     * @param filterChain 继续处理的过滤器链
     * @throws ServletException 如果发生 Servlet 错误
     * @throws IOException      如果发生 I/O 错误
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uidHeader = request.getHeader(BusinessConstants.USER_ID_HEADER);
        if (uidHeader != null && !uidHeader.isBlank()) {
            try {
                UserContext.set(Long.parseLong(uidHeader));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
