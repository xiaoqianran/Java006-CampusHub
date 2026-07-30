package com.shiqian.common.security;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具。仅用于阻止凭据进入日志，不用于业务数据加密。
 */
public final class SensitiveDataMasker {

    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)([\"']?(?:password|oldPassword|newPassword|token|accessToken|refreshToken|authorization)[\"']?\\s*[:=]\\s*[\"']?)([^\"'&,}\\s]+)");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+");

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        String masked = BEARER_TOKEN.matcher(value).replaceAll("Bearer ***");
        return JSON_SECRET.matcher(masked).replaceAll("$1***");
    }
}
