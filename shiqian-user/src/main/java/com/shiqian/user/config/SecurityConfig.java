package com.shiqian.user.config;

import com.shiqian.user.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/user/register",
                    "/api/user/login",
                    "/api/user/refresh",
                    "/api/user/health",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/internal/users/public-profiles/batch"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/internal/users/*/authorities"
                ).permitAll()
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
