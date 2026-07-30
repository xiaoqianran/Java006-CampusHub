package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.resource.assembler.ResourceResponseAssembler;
import com.shiqian.resource.dto.TagDTO;
import com.shiqian.resource.service.TagService;
import com.shiqian.resource.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "标签管理", description = "资源标签查询与后台维护")
public class TagController {

    private final TagService tagService;
    private final ResourceResponseAssembler responseAssembler;

    @Operation(summary = "查询可用标签")
    @GetMapping
    public Result<List<TagVO>> listTags(@RequestParam(required = false) String keyword) {
        return Result.ok(responseAssembler.toTagVOs(tagService.listTags(keyword)));
    }

    @Operation(summary = "新增标签")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<TagVO> addTag(@RequestBody @Valid TagDTO dto) {
        return Result.ok(responseAssembler.toTagVO(tagService.addTag(dto.getName())));
    }

    @Operation(summary = "更新标签")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<TagVO> updateTag(
            @PathVariable @Positive Long id,
            @RequestBody @Valid TagDTO dto) {
        return Result.ok(responseAssembler.toTagVO(
                tagService.updateTag(id, dto.getName())));
    }

    @Operation(summary = "删除标签并清理资源关系")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> deleteTag(@PathVariable @Positive Long id) {
        tagService.deleteTag(id);
        return Result.ok();
    }
}
