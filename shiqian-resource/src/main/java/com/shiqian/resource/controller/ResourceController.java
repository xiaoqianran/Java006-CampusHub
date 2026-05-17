package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.config.RabbitMQConfig;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.FavoriteService;
import com.shiqian.resource.service.ResourceSearchService;
import com.shiqian.resource.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "资源管理", description = "资源上传、查询、更新、删除、下载、收藏等接口")
@Slf4j
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final FavoriteService favoriteService;
    private final ResourceSearchService resourceSearchService;
    private final RabbitTemplate rabbitTemplate;

    @Operation(summary = "创建资源")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:create')")
    public Result<Void> createResource(@RequestBody @Valid ResourceCreateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.createResource(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "分页查询资源列表")
    @GetMapping
    public Result<Page<Resource>> pageResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        Page<Resource> result = resourceService.pageResources(page, size, categoryId, keyword);
        return Result.ok(result);
    }

    @Operation(summary = "根据ID获取资源详情")
    @GetMapping("/{id}")
    public Result<Resource> getResourceById(@PathVariable Long id) {
        Resource resource = resourceService.getResourceById(id);
        return Result.ok(resource);
    }

    @Operation(summary = "更新资源")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<Void> updateResource(@PathVariable Long id,
                                       @RequestBody @Valid ResourceUpdateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.updateResource(userId, id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除资源")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:delete')")
    public Result<Void> deleteResource(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.deleteResource(userId, id);
        return Result.ok();
    }

    @Operation(summary = "下载资源")
    @PostMapping("/{id}/download")
    public Result<Void> downloadResource(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            userId = 1L;
        }
        ResourceDownloadMessage message = new ResourceDownloadMessage(id, userId, LocalDateTime.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE,
                RabbitMQConfig.RESOURCE_DOWNLOAD_ROUTING_KEY,
                message);
        return Result.ok();
    }

    @Operation(summary = "收藏资源")
    @PostMapping("/{id}/favorite")
    public Result<Void> addFavorite(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        favoriteService.addFavorite(userId, id);
        return Result.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}/favorite")
    public Result<Void> removeFavorite(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        favoriteService.removeFavorite(userId, id);
        return Result.ok();
    }

    @Operation(summary = "查询是否已收藏")
    @GetMapping("/{id}/favorite")
    public Result<Boolean> isFavorited(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(favoriteService.isFavorited(userId, id));
    }

    @Operation(summary = "搜索资源")
    @GetMapping("/search")
    public Result<org.springframework.data.domain.Page<ResourceDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        org.springframework.data.domain.Page<ResourceDocument> result = resourceSearchService.search(keyword, page, size);
        return Result.ok(result);
    }

    @Operation(summary = "审核资源")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> auditResource(@PathVariable Long id,
                                      @RequestParam Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        if (operatorId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.auditResource(id, status, operatorId);
        return Result.ok();
    }
}
