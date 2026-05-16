package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
