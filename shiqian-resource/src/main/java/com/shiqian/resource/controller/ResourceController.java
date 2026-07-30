package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.ratelimit.DistributedRateLimit;
import com.shiqian.common.ratelimit.RateLimitKeyMode;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.dto.FileDownloadVO;
import com.shiqian.resource.dto.IndexConsistencyVO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceRollbackDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.dto.ResourceVersionVO;
import com.shiqian.resource.dto.ResourceReviewDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.FavoriteService;
import com.shiqian.resource.service.ResourceSearchService;
import com.shiqian.resource.service.ResourceService;
import com.shiqian.resource.service.ResourceVersionService;
import com.shiqian.resource.service.ResourceMessagePublisher;
import com.shiqian.resource.service.ResourceIndexMaintenanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Tag(name = "资源管理", description = "资源上传、查询、更新、删除、下载、收藏等接口")
@Slf4j
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
@Validated
public class ResourceController {

    private final ResourceService resourceService;
    private final FavoriteService favoriteService;
    private final ResourceSearchService resourceSearchService;
    private final ResourceMessagePublisher messagePublisher;
    private final ResourceVersionService resourceVersionService;
    private final ResourceIndexMaintenanceService indexMaintenanceService;

    @Operation(summary = "创建资源")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:create')")
    @DistributedRateLimit(name = "resource:publish", limit = 10, windowSeconds = 60, keyMode = RateLimitKeyMode.USER)
    public Result<Long> createResource(@RequestBody @Valid ResourceCreateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        Resource resource = resourceService.createResource(userId, dto);
        return Result.ok(resource.getId());
    }

    @Operation(summary = "分页查询资源列表")
    @GetMapping
    public Result<Page<Resource>> pageResources(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) @Size(max = 50) String tag) {
        Page<Resource> result = SecurityUtil.hasAuthority("resource:audit")
                ? resourceService.pageResources(
                        page, size, categoryId, keyword, sort, scene, tagId, tag)
                : resourceService.pagePublishedResources(
                        page, size, categoryId, keyword, sort, scene, tagId, tag);
        return Result.ok(result);
    }

    @Operation(summary = "分页查询当前用户发布的资源")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('resource:read')")
    public Result<Page<Resource>> pageMyResources(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String sort) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(resourceService.pageUserResources(userId, page, size, sort));
    }

    @Operation(summary = "分页查询当前用户收藏的资源")
    @GetMapping("/favorites")
    @PreAuthorize("hasAuthority('resource:favorite')")
    public Result<Page<Resource>> pageFavoriteResources(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String sort) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(favoriteService.pageFavorites(userId, page, size, sort));
    }

    @Operation(summary = "管理员分页查询回收站资源")
    @GetMapping("/recycle-bin")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Page<Resource>> pageRecycleResources(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(resourceService.pageRecycleResources(page, size, keyword));
    }

    @Operation(summary = "从回收站恢复资源")
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> restoreResource(@PathVariable @Positive Long id) {
        resourceService.restoreResource(id);
        return Result.ok();
    }

    @Operation(summary = "永久删除资源（不可恢复）")
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> permanentDeleteResource(@PathVariable @Positive Long id) {
        resourceService.permanentDeleteResource(id);
        return Result.ok();
    }

    @Operation(summary = "根据ID获取资源详情")
    @GetMapping("/{id}")
    public Result<Resource> getResourceById(@PathVariable @Positive Long id) {
        Resource resource = resourceService.getResourceById(id);
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在或已删除");
        }
        if (!canViewResource(resource)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在或已删除");
        }
        return Result.ok(resource);
    }

    private boolean canViewResource(Resource resource) {
        if (resource.getStatus() != null && resource.getStatus() == 1) {
            return true;
        }
        Long userId = SecurityUtil.getCurrentUserId();
        return resource.getUserId().equals(userId)
                || SecurityUtil.hasAuthority("resource:audit");
    }

    @Operation(summary = "更新资源")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<Void> updateResource(@PathVariable @Positive Long id,
                                       @RequestBody @Valid ResourceUpdateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.updateResource(userId, id, dto);
        return Result.ok();
    }

    @Operation(summary = "查询资源版本历史")
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<List<ResourceVersionVO>> listVersions(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.ok(resourceVersionService.listVersions(userId, id));
    }

    @Operation(summary = "查询指定资源版本")
    @GetMapping("/{id}/versions/{version}")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<ResourceVersionVO> getVersion(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Integer version) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.ok(resourceVersionService.getVersion(userId, id, version));
    }

    @Operation(summary = "回滚到指定资源版本（回滚会生成新版本并重新进入审核）")
    @PostMapping("/{id}/versions/{version}/rollback")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<Integer> rollbackVersion(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Integer version,
            @RequestBody(required = false) @Valid ResourceRollbackDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Resource rolledBack = resourceVersionService.rollback(
                userId,
                id,
                version,
                dto == null ? null : dto.getChangeDescription());
        return Result.ok(rolledBack.getVersion());
    }

    @Operation(summary = "重新提交待修改资源")
    @PutMapping("/{id}/resubmit")
    @PreAuthorize("hasAuthority('resource:update')")
    public Result<Void> resubmitResource(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.resubmitResource(userId, id);
        return Result.ok();
    }

    @Operation(summary = "删除资源")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:delete')")
    public Result<Void> deleteResource(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        resourceService.deleteResource(userId, id);
        return Result.ok();
    }

    @Operation(summary = "下载资源")
    @PostMapping("/{id}/download")
    @DistributedRateLimit(name = "resource:download", limit = 30, windowSeconds = 60)
    public Result<FileDownloadVO> downloadResource(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Resource resource = resourceService.getResourceById(id);
        if (resource == null || !canViewResource(resource)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在或已删除");
        }
        boolean hasPrimaryFile = org.springframework.util.StringUtils.hasText(resource.getFileUrl());
        boolean hasAttachments = resource.getAttachments() != null && !resource.getAttachments().isEmpty();
        if (!hasPrimaryFile && !hasAttachments) {
            return Result.fail(400, "资源暂无可下载文件");
        }

        messagePublisher.publishDownload(id, userId);

        FileDownloadVO vo = new FileDownloadVO();
        vo.setResourceId(resource.getId());
        vo.setTitle(resource.getTitle());
        vo.setFileUrl(resource.getFileUrl());
        vo.setFileSize(resource.getFileSize());
        vo.setFileType(resource.getFileType());
        vo.setAttachments(resource.getAttachments());
        return Result.ok(vo);
    }

    @Operation(summary = "记录资源浏览次数（支持匿名访问，详情页加载后调用）")
    @PostMapping("/{id}/view")
    @DistributedRateLimit(name = "resource:view", limit = 120, windowSeconds = 60)
    public Result<Void> viewResource(@PathVariable @Positive Long id) {
        // 直接更新计数（与下载不同，不使用MQ），不要求登录；存在性由service校验
        resourceService.incrementViewCount(id);
        return Result.ok();
    }

    @Operation(summary = "收藏资源")
    @PostMapping("/{id}/favorite")
    @DistributedRateLimit(name = "resource:favorite", limit = 30, windowSeconds = 60, keyMode = RateLimitKeyMode.USER)
    public Result<Void> addFavorite(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        favoriteService.addFavorite(userId, id);
        return Result.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}/favorite")
    public Result<Void> removeFavorite(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        favoriteService.removeFavorite(userId, id);
        return Result.ok();
    }

    @Operation(summary = "查询是否已收藏")
    @GetMapping("/{id}/favorite")
    public Result<Boolean> isFavorited(@PathVariable @Positive Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(favoriteService.isFavorited(userId, id));
    }

    @Operation(summary = "搜索资源")
    @GetMapping("/search")
    @DistributedRateLimit(name = "resource:search", limit = 60, windowSeconds = 60)
    public Result<Page<Resource>> search(
            @RequestParam @NotBlank @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) @Size(max = 50) String tag) {
        org.springframework.data.domain.Page<ResourceDocument> searchResult =
                resourceSearchService.search(
                        keyword, page, size, sort, scene, categoryId, tagId, tag);
        List<Long> orderedIds = searchResult.getContent().stream()
                .map(ResourceDocument::getId)
                .toList();
        List<Resource> resources = resourceService.getPublishedResourcesByIds(orderedIds, scene);
        java.util.Map<Long, java.util.Map<String, List<String>>> highlights =
                searchResult.getContent().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ResourceDocument::getId,
                                document -> document.getHighlights() == null
                                        ? java.util.Map.of()
                                        : document.getHighlights(),
                                (left, right) -> left));
        resources.forEach(resource ->
                resource.setSearchHighlights(highlights.getOrDefault(
                        resource.getId(), java.util.Map.of())));
        Page<Resource> result = new Page<>(page, size, searchResult.getTotalElements());
        result.setRecords(resources);
        return Result.ok(result);
    }

    @Operation(summary = "管理员重建 Elasticsearch 资源索引")
    @PostMapping("/index/rebuild")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Long> rebuildIndex() {
        return Result.ok(indexMaintenanceService.rebuildIndex());
    }

    @Operation(summary = "检查 MySQL 与 Elasticsearch 资源索引一致性")
    @GetMapping("/index/consistency")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<IndexConsistencyVO> checkIndexConsistency() {
        return Result.ok(indexMaintenanceService.checkConsistency());
    }

    @Operation(summary = "审核或调整资源状态")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('resource:audit')")
    @DistributedRateLimit(name = "resource:audit", limit = 30, windowSeconds = 60, keyMode = RateLimitKeyMode.USER)
    public Result<Void> auditResource(@PathVariable @Positive Long id,
                                      @RequestParam(required = false) Integer status,
                                      @RequestBody(required = false) @Valid ResourceReviewDTO reviewDTO) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        if (operatorId == null) {
            return Result.fail(401, "未登录");
        }
        if (reviewDTO != null) {
            resourceService.reviewResource(
                    id,
                    reviewDTO.getStatus() != null ? reviewDTO.getStatus() : status,
                    reviewDTO.getReason(),
                    operatorId);
        } else {
            // 兼容旧客户端的 ?status= 请求方式。
            resourceService.auditResource(id, status, operatorId);
        }
        return Result.ok();
    }
}
