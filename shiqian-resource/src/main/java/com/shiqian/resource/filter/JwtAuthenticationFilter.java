package com.shiqian.resource.filter;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.common.security.LoginUser;
import com.shiqian.common.security.PermissionEnum;
import com.shiqian.common.security.RolePermissionMapping;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                if (claims != null) {
                    Long userId = claims.get("userId", Long.class);
                    String username = claims.get("username", String.class);
                    String role = claims.get("role", String.class);

                    LoginUser loginUser = new LoginUser(userId, username, role);

                    List<PermissionEnum> permissions = RolePermissionMapping.getPermissions(role);
                    List<SimpleGrantedAuthority> authorities = permissions.stream()
                            .map(p -> new SimpleGrantedAuthority(p.getCode()))
                            .collect(Collectors.toList());
                    authorities.add(new SimpleGrantedAuthority(
                            "ROLE_" + (role != null ? role : "USER")));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.error("Failed to process JWT token: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
