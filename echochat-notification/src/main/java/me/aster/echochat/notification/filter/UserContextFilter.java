package me.aster.echochat.notification.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.context.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet过滤器，从传入请求中提取{@code X-User-Id}请求头，
 * 在请求期间存入{@link UserContext}，请求完成后清理上下文。
 * @author AsterWinston
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 读取{@code X-User-Id}请求头，设置线程本地用户上下文，
     * 执行过滤器链，然后在finally块中清除上下文。
     *
     * @param request     HTTP请求
     * @param response    HTTP响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(BusinessConstants.USER_ID_HEADER);
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                UserContext.set(Long.parseLong(userIdHeader));
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
