package com.shiqian.common.security;

/**
 * 各服务共享的 Redis 鉴权键规范。
 */
public final class TokenKey {

    public static final String USER_VERSION_PREFIX = "auth:user:version:";
    public static final String REFRESH_PREFIX = "auth:refresh:";
    public static final String USER_REFRESH_PREFIX = "auth:user:refresh:";
    public static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";
    public static final String USER_AUTHORITIES_PREFIX = "auth:user:authorities:";
    public static final String EMPTY_AUTHORITIES_MARKER = "__EMPTY_AUTHORITIES__";

    private TokenKey() {
    }

    public static String userVersion(Long userId) {
        return USER_VERSION_PREFIX + userId;
    }

    public static String refresh(String jti) {
        return REFRESH_PREFIX + jti;
    }

    public static String userRefresh(Long userId) {
        return USER_REFRESH_PREFIX + userId;
    }

    public static String accessBlacklist(String jti) {
        return ACCESS_BLACKLIST_PREFIX + jti;
    }

    public static String userAuthorities(Long userId) {
        return USER_AUTHORITIES_PREFIX + userId;
    }
}
