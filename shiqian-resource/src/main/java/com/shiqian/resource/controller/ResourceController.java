package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final FavoriteService favoriteService;
    private final ResourceSearchService resourceSearchService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    public Result<Void> createResource(@RequestBody @Valid ResourceCreateDTO dto) {
        Long userId = 1L;
        resourceService.createResource(userId, dto);
        return Result.ok();
    }

    @GetMapping
    public Result<Page<Resource>> pageResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        Page<Resource> result = resourceService.pageResources(page, size, categoryId, keyword);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Resource> getResourceById(@PathVariable Long id) {
        Resource resource = resourceService.getResourceById(id);
        return Result.ok(resource);
    }

    @PutMapping("/{id}")
    public Result<Void> updateResource(@PathVariable Long id,
                                       @RequestBody @Valid ResourceUpdateDTO dto) {
        Long userId = 1L;
        resourceService.updateResource(userId, id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteResource(@PathVariable Long id) {
        Long userId = 1L;
        resourceService.deleteResource(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/download")
    public Result<Void> downloadResource(@PathVariable Long id) {
        Long userId = 1L;
        ResourceDownloadMessage message = new ResourceDownloadMessage(id, userId, LocalDateTime.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESOURCE_TOPIC_EXCHANGE,
                RabbitMQConfig.RESOURCE_DOWNLOAD_ROUTING_KEY,
                message);
        return Result.ok();
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> addFavorite(@PathVariable Long id) {
        Long userId = 1L;
        favoriteService.addFavorite(userId, id);
        return Result.ok();
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Void> removeFavorite(@PathVariable Long id) {
        Long userId = 1L;
        favoriteService.removeFavorite(userId, id);
        return Result.ok();
    }

    @GetMapping("/{id}/favorite")
    public Result<Boolean> isFavorited(@PathVariable Long id) {
        Long userId = 1L;
        return Result.ok(favoriteService.isFavorited(userId, id));
    }

    @GetMapping("/search")
    public Result<org.springframework.data.domain.Page<ResourceDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        org.springframework.data.domain.Page<ResourceDocument> result = resourceSearchService.search(keyword, page, size);
        return Result.ok(result);
    }

    @PutMapping("/{id}/audit")
    public Result<Void> auditResource(@PathVariable Long id,
                                      @RequestParam Integer status) {
        Long operatorId = 1L;
        resourceService.auditResource(id, status, operatorId);
        return Result.ok();
    }
}
