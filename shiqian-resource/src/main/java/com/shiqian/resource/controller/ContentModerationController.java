package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.assembler.ResourceResponseAssembler;
import com.shiqian.resource.dto.SensitiveWordDTO;
import com.shiqian.resource.service.ContentReviewService;
import com.shiqian.resource.service.SensitiveWordService;
import com.shiqian.resource.vo.ContentReviewRecordVO;
import com.shiqian.resource.vo.SensitiveWordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/admin/content-moderation")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('resource:audit')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "内容安全管理", description = "敏感词热更新和自动/人工审核记录")
public class ContentModerationController {

    private final SensitiveWordService sensitiveWordService;
    private final ContentReviewService contentReviewService;
    private final ResourceResponseAssembler responseAssembler;

    @Operation(summary = "查询敏感词规则")
    @GetMapping("/sensitive-words")
    public Result<List<SensitiveWordVO>> listWords(
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return Result.ok(responseAssembler.toSensitiveWordVOs(
                sensitiveWordService.list(keyword)));
    }

    @Operation(summary = "新增敏感词规则")
    @PostMapping("/sensitive-words")
    public Result<Long> createWord(@RequestBody @Valid SensitiveWordDTO dto) {
        return Result.ok(sensitiveWordService.create(dto, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "更新敏感词规则")
    @PutMapping("/sensitive-words/{id}")
    public Result<Void> updateWord(
            @PathVariable @Positive Long id,
            @RequestBody @Valid SensitiveWordDTO dto) {
        sensitiveWordService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除敏感词规则")
    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteWord(@PathVariable @Positive Long id) {
        sensitiveWordService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "从数据库热加载敏感词规则")
    @PostMapping("/sensitive-words/reload")
    public Result<Void> reloadWords() {
        sensitiveWordService.reload();
        return Result.ok();
    }

    @Operation(summary = "分页查询自动和人工审核记录")
    @GetMapping("/records")
    public Result<Page<ContentReviewRecordVO>> records(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String reviewType,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) Long resourceId) {
        return Result.ok(responseAssembler.toContentReviewRecordPage(
                contentReviewService.pageRecords(
                        page, size, reviewType, decision, resourceId)));
    }
}
