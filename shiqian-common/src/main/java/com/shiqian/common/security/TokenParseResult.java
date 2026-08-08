package com.shiqian.common.security;

import io.jsonwebtoken.Claims;

/**
 * JWT 解析结果：区分过期与非法，便于客户端决定是否刷新。
 */
public final class TokenParseResult {

    public enum Failure {
        EXPIRED("token_expired", "登录已过期，请刷新令牌"),
        INVALID("token_invalid", "未登录或 token 无效");

        private final String code;
        private final String message;

        Failure(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }
    }

    private final Claims claims;
    private final Failure failure;

    private TokenParseResult(Claims claims, Failure failure) {
        this.claims = claims;
        this.failure = failure;
    }

    public static TokenParseResult success(Claims claims) {
        return new TokenParseResult(claims, null);
    }

    public static TokenParseResult failure(Failure failure) {
        return new TokenParseResult(null, failure);
    }

    public boolean isSuccess() {
        return claims != null;
    }

    public Claims claims() {
        return claims;
    }

    public Failure failure() {
        return failure;
    }
}
