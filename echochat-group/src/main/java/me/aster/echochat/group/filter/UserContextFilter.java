package me.aster.echochat.group.filter;

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
 * 从入站请求中提取X-User-Id头并存储到 {@link UserContext} 中。请求完成后清除上下文。
 * @author AsterWinston
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * @param request     入站HTTP请求
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
