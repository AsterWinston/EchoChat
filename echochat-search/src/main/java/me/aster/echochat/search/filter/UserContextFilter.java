package me.aster.echochat.search.filter;

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
 * 每个请求执行一次的过滤器，提取X-User-Id请求头，在请求期间存入{@link UserContext}，
 * 然后在finally块中清除。
 * @author AsterWinston
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 提取X-User-Id请求头，若其值为有效数字则设置到线程本地上下文；
     * 若值无效则直接忽略。继续执行过滤器链，并在最后进行清理。
     *
     * @param request     HTTP请求
     * @param response    HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet错误时抛出
     * @throws IOException      I/O错误时抛出
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
