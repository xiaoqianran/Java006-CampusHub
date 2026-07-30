package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.content.SensitiveWordFilter;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.entity.ContentReviewRecord;
import com.shiqian.resource.mapper.ContentReviewRecordMapper;
import com.shiqian.resource.service.ContentReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentReviewServiceImpl implements ContentReviewService {

    private final SensitiveWordFilter filter;
    private final ContentReviewRecordMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void inspectOrReject(
            Long submitterId,
            Long resourceId,
            String title,
            String summary,
            String content,
            String tags) {
        Set<String> matches = new LinkedHashSet<>();
        matches.addAll(filter.findAll(title));
        matches.addAll(filter.findAll(summary));
        matches.addAll(filter.findAll(content));
        matches.addAll(filter.findAll(tags));
        if (!matches.isEmpty()) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(ignored ->
                    recordAutoBlock(submitterId, resourceId, title, matches));
            throw new BusinessException("资源内容包含敏感词");
        }
    }

    private void recordAutoBlock(
            Long submitterId,
            Long resourceId,
            String title,
            Set<String> matches) {
        ContentReviewRecord record = base(resourceId, submitterId, null, "AUTO", "BLOCKED");
        record.setMatchedWords(String.join(",", matches));
        record.setReason("命中内容安全规则");
        record.setContentTitle(truncate(title, 200));
        mapper.insert(record);
    }

    @Override
    @Transactional
    public void recordManual(
            Long resourceId,
            Long submitterId,
            Long reviewerId,
            Integer status,
            String reason) {
        String decision = switch (status == null ? -1 : status) {
            case 1 -> "APPROVED";
            case 2 -> "NEEDS_CHANGES";
            case 3 -> "REJECTED";
            case 4 -> "OFFLINE";
            default -> "REVIEWED";
        };
        ContentReviewRecord record = base(resourceId, submitterId, reviewerId, "MANUAL", decision);
        record.setReason(truncate(reason, 500));
        mapper.insert(record);
    }

    @Override
    public Page<ContentReviewRecord> pageRecords(
            int page,
            int size,
            String reviewType,
            String decision,
            Long resourceId) {
        QueryWrapper<ContentReviewRecord> query = new QueryWrapper<>();
        query.eq(StringUtils.hasText(reviewType), "review_type", reviewType);
        query.eq(StringUtils.hasText(decision), "decision", decision);
        query.eq(resourceId != null, "resource_id", resourceId);
        query.orderByDesc("create_time").orderByDesc("id");
        return mapper.selectPage(new Page<>(page, Math.min(size, 100)), query);
    }

    private ContentReviewRecord base(
            Long resourceId,
            Long submitterId,
            Long reviewerId,
            String type,
            String decision) {
        ContentReviewRecord record = new ContentReviewRecord();
        record.setResourceId(resourceId);
        record.setSubmitterId(submitterId);
        record.setReviewerId(reviewerId);
        record.setReviewType(type);
        record.setDecision(decision);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private String truncate(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
