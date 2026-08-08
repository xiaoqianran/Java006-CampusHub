package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.security.SensitiveDataMasker;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.mapper.AdminLogMapper;
import com.shiqian.resource.service.AdminLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl implements AdminLogService {

    private final AdminLogMapper adminLogMapper;

    @Override
    public void recordLog(Long operatorId, String action, Long targetId, String detail) {
        if (action == null || action.isBlank()) {
            return;
        }
        AdminLog log = new AdminLog();
        log.setOperatorId(operatorId != null ? operatorId : 0L);
        log.setOperatorName(operatorId != null ? "用户#" + operatorId : "系统");
        log.setAction(action);
        log.setTargetType(action.startsWith("RESOURCE") ? "RESOURCE" : "SYSTEM");
        log.setTargetId(targetId);
        log.setDetail(SensitiveDataMasker.mask(detail));
        log.setResult("SUCCESS");
        log.setCreateTime(LocalDateTime.now());
        HttpServletRequest request = currentRequest();
        if (request != null) {
            log.setRequestMethod(request.getMethod());
            log.setRequestUri(request.getRequestURI());
            log.setRequestIp(resolveClientIp(request));
            log.setRequestParams(SensitiveDataMasker.mask(formatParameters(request.getParameterMap())));
        }
        adminLogMapper.insert(log);
    }

    @Override
    public Page<AdminLog> pageLogs(
            Integer page,
            Integer size,
            String action,
            Long operatorId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, 100);
        QueryWrapper<AdminLog> wrapper = new QueryWrapper<>();
        wrapper.eq(action != null && !action.isBlank(), "operation_type", action);
        wrapper.eq(operatorId != null, "operator_id", operatorId);
        wrapper.ge(startTime != null, "create_time", startTime);
        wrapper.le(endTime != null, "create_time", endTime);
        wrapper.orderByDesc("create_time").orderByDesc("id");
        return adminLogMapper.selectPage(new Page<>(p, s), wrapper);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return com.shiqian.common.security.ClientIpResolver.resolve(request);
    }

    private String formatParameters(Map<String, String[]> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + Arrays.toString(entry.getValue()))
                .collect(Collectors.joining("&"));
    }
}
