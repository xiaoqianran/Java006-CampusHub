package com.shiqian.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对会创建/轮换浏览器认证 Cookie 的接口执行精确 Origin 校验。
 *
 * <p>当 Refresh Cookie 使用 SameSite=None 以支持 GitHub Pages 等跨站前端时，
 * CORS 本身不能替代 CSRF 防护；该过滤器阻止不可信网页触发登录/刷新/退出。</p>
 */
@Component
public class BrowserAuthOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/user/login",
            "/api/user/refresh",
            "/api/user/logout"
    );

    private final Set<String> allowedOrigins;

    public BrowserAuthOriginFilter(
            @Value("${campushub.auth.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");

        // curl、服务端 SDK 等非浏览器客户端通常没有 Origin；仍允许按现有 API 方式调用。
        if (!StringUtils.hasText(origin) || allowedOrigins.contains(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":403,\"message\":\"不可信的请求来源\",\"data\":null,\"success\":false}");
    }
}
