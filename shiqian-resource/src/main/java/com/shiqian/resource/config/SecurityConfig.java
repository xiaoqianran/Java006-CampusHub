package com.shiqian.resource.config;

import com.shiqian.resource.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security 配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPeriod(true);
        return firewall;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall httpFirewall) {
        return web -> web.httpFirewall(httpFirewall);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 受保护资源 GET 必须先于通配 permitAll，否则过期 JWT 会变成 403 而非 401。
                .requestMatchers(HttpMethod.GET,
                        "/api/resource/mine",
                        "/api/resource/favorites",
                        "/api/resource/recycle-bin",
                        "/api/resource/*/favorite",
                        "/api/resource/*/versions",
                        "/api/resource/*/versions/**",
                        "/api/resource/index/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/resource/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/resource/*/download").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/resource/*/view").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/category/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tag/**").permitAll()
                // 油猴同步接口：控制器内限制仅本机直连
                .requestMatchers("/api/jimeng/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/favicon.ico").permitAll()
                .requestMatchers("/api/resource/**").authenticated()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"code\":401,\"message\":\"未登录或 token 已过期\",\"data\":null,\"success\":false}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 匿名访问受保护方法时返回 401，便于前端走 refresh 流程
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean anonymous = auth == null
                            || !auth.isAuthenticated()
                            || auth instanceof AnonymousAuthenticationToken;
                    response.setContentType("application/json;charset=UTF-8");
                    if (anonymous) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write(
                                "{\"code\":401,\"message\":\"未登录或 token 已过期\",\"data\":null,\"success\":false}");
                    } else {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write(
                                "{\"code\":403,\"message\":\"无权限访问\",\"data\":null,\"success\":false}");
                    }
                })
            );

        return http.build();
    }
}
