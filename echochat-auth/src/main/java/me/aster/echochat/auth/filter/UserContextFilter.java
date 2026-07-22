package me.aster.echochat.auth.filter;

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
 * Servlet 过滤器，从 X-User-Id 请求头中提取用户 ID 并存入 {@link UserContext}。
 * @author AsterWinston
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 解析 X-User-Id 请求头，在请求期间设置到线程局部上下文中。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
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
