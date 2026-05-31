package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.service.AdminLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 轻量级管理员操作审计日志接口
 * 仅管理员可访问，POST 用于内部/前端记录关键操作
 */
@Tag(name = "管理员审计日志", description = "轻量级操作日志查询与记录")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "记录管理员操作日志（内部使用）")
    @PostMapping("/logs")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> recordLog(@RequestBody Map<String, Object> body) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        String action = body != null ? (String) body.get("action") : null;
        Long targetId = body != null && body.get("targetId") != null ? Long.valueOf(body.get("targetId").toString()) : null;
        String detail = body != null ? (String) body.get("detail") : null;
        adminLogService.recordLog(operatorId, action, targetId, detail);
        return Result.ok();
    }

    @Operation(summary = "分页查询管理员操作日志")
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Page<AdminLog>> listLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String action) {
        Page<AdminLog> result = adminLogService.pageLogs(page, size, action);
        return Result.ok(result);
    }
}
