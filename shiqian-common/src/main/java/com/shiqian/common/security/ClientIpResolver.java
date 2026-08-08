package com.shiqian.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 解析客户端 IP：仅当直连对端为受信代理（回环/内网）时才读取转发头，
 * 且优先取链路上最靠近本服务的非代理段，避免客户端伪造 X-Forwarded-For 首段。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = normalize(request.getRemoteAddr());
        if (!isTrustedPeer(remote)) {
            return remote != null ? remote : "unknown";
        }

        String forwarded = firstNonBlank(
                request.getHeader("X-Real-IP"),
                request.getHeader("X-Forwarded-For"));
        if (!StringUtils.hasText(forwarded)) {
            return remote != null ? remote : "unknown";
        }

        String[] parts = forwarded.split(",");
        // 自右向左跳过受信代理自身，取客户端侧第一段。
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalize(parts[i].trim());
            if (candidate != null && !isTrustedPeer(candidate)) {
                return candidate;
            }
        }
        String last = normalize(parts[parts.length - 1].trim());
        return last != null ? last : (remote != null ? remote : "unknown");
    }

    /**
     * 即梦等仅本机写入的接口：只认直连地址，绝不信任转发头。
     */
    public static boolean isDirectLoopback(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return isLoopback(normalize(request.getRemoteAddr()));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String ip = value.trim();
        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']'));
        }
        int zone = ip.indexOf('%');
        if (zone > 0) {
            ip = ip.substring(0, zone);
        }
        if ("::ffff:127.0.0.1".equalsIgnoreCase(ip)) {
            return "127.0.0.1";
        }
        if (ip.regionMatches(true, 0, "::ffff:", 0, 7)) {
            return ip.substring(7);
        }
        return ip;
    }

    private static boolean isTrustedPeer(String ip) {
        return isLoopback(ip) || isPrivate(ip);
    }

    private static boolean isLoopback(String ip) {
        if (ip == null) {
            return false;
        }
        return "127.0.0.1".equals(ip)
                || "https://example.net/id/garnet".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    private static boolean isPrivate(String ip) {
        if (ip == null) {
            return false;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe80:");
    }
}
