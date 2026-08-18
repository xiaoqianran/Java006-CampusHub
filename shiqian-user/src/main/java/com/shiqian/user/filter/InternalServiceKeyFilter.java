package com.shiqian.user.filter;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.common.user.InternalApiHeaders;
import com.shiqian.user.security.InternalServiceKeyValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一保护所有 /internal/** 服务间接口，避免 Controller 新增端点时遗漏鉴权。
 */
@Component
@RequiredArgsConstructor
public class InternalServiceKeyFilter extends OncePerRequestFilter {

    private final InternalServiceKeyValidator validator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            validator.validate(request.getHeader(InternalApiHeaders.SERVICE_KEY));
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":403,\"message\":\"服务间调用凭据无效\",\"data\":null,\"success\":false}");
        }
    }
}
