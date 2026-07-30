package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.resource.dto.TagDTO;
import com.shiqian.resource.entity.Tag;
import com.shiqian.resource.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final TagService tagService;

    @Operation(summary = "查询可用标签")
    @GetMapping
    public Result<List<Tag>> listTags(@RequestParam(required = false) String keyword) {
        return Result.ok(tagService.listTags(keyword));
    }

    @Operation(summary = "新增标签")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Tag> addTag(@RequestBody @Valid TagDTO dto) {
        return Result.ok(tagService.addTag(dto.getName()));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Tag> updateTag(
            @PathVariable @Positive Long id,
            @RequestBody @Valid TagDTO dto) {
        return Result.ok(tagService.updateTag(id, dto.getName()));
    }

    @Operation(summary = "删除标签并清理资源关系")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> deleteTag(@PathVariable @Positive Long id) {
        tagService.deleteTag(id);
        return Result.ok();
    }
}
