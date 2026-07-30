package com.shiqian.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户在当前时刻生效的角色与权限快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthoritySnapshot {

    private Set<String> roles = new LinkedHashSet<>();
    private Set<String> permissions = new LinkedHashSet<>();

    public Set<String> asGrantedAuthorities() {
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> "ROLE_" + role)
                    .forEach(authorities::add);
        }
        if (permissions != null) {
            permissions.stream()
                    .filter(permission -> permission != null && !permission.isBlank())
                    .forEach(authorities::add);
        }
        return authorities;
    }

    public static AuthoritySnapshot fromGrantedAuthorities(Set<String> authorities) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (authorities != null) {
            authorities.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> {
                        if (value.startsWith("ROLE_")) {
                            roles.add(value.substring("ROLE_".length()));
                        } else {
                            permissions.add(value);
                        }
                    });
        }
        return new AuthoritySnapshot(roles, permissions);
    }
}
