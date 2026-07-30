package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.assembler.ResourceResponseAssembler;
import com.shiqian.resource.dto.AdminLogCreateDTO;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.service.AdminLogService;
import com.shiqian.resource.vo.AdminLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;

import java.time.LocalDateTime;

/**
 * 轻量级管理员操作审计日志接口
 * 仅管理员可访问，POST 用于内部/前端记录关键操作
 */
@Tag(name = "管理员审计日志", description = "轻量级操作日志查询与记录")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
public class AdminLogController {

    private final AdminLogService adminLogService;
    private final ResourceResponseAssembler responseAssembler;

    @Operation(summary = "记录管理员操作日志（内部使用）")
    @PostMapping("/logs")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> recordLog(@RequestBody @Valid AdminLogCreateDTO body) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        adminLogService.recordLog(
                operatorId,
                body.getAction(),
                body.getTargetId(),
                body.getDetail());
        return Result.ok();
    }

    @Operation(summary = "分页查询管理员操作日志")
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Page<AdminLogVO>> listLogs(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Page<AdminLog> result = adminLogService.pageLogs(
                page, size, action, operatorId, startTime, endTime);
        return Result.ok(responseAssembler.toAdminLogPage(result));
    }
}
