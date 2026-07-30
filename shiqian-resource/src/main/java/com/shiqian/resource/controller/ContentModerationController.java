package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.dto.SensitiveWordDTO;
import com.shiqian.resource.entity.ContentReviewRecord;
import com.shiqian.resource.entity.SensitiveWord;
import com.shiqian.resource.service.ContentReviewService;
import com.shiqian.resource.service.SensitiveWordService;
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
public class ContentModerationController {

    private final SensitiveWordService sensitiveWordService;
    private final ContentReviewService contentReviewService;

    @GetMapping("/sensitive-words")
    public Result<List<SensitiveWord>> listWords(
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return Result.ok(sensitiveWordService.list(keyword));
    }

    @PostMapping("/sensitive-words")
    public Result<Long> createWord(@RequestBody @Valid SensitiveWordDTO dto) {
        return Result.ok(sensitiveWordService.create(dto, SecurityUtil.getCurrentUserId()));
    }

    @PutMapping("/sensitive-words/{id}")
    public Result<Void> updateWord(
            @PathVariable @Positive Long id,
            @RequestBody @Valid SensitiveWordDTO dto) {
        sensitiveWordService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteWord(@PathVariable @Positive Long id) {
        sensitiveWordService.delete(id);
        return Result.ok();
    }

    @PostMapping("/sensitive-words/reload")
    public Result<Void> reloadWords() {
        sensitiveWordService.reload();
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<Page<ContentReviewRecord>> records(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String reviewType,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) Long resourceId) {
        return Result.ok(contentReviewService.pageRecords(
                page, size, reviewType, decision, resourceId));
    }
}
