package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

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
}
