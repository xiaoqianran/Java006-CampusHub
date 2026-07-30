package com.shiqian.common.security;

/**
 * JWT 用途。访问令牌和刷新令牌必须严格区分，不能互相替代。
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
