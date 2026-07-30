package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.ContentReviewRecord;

public interface ContentReviewService {
    void inspectOrReject(
            Long submitterId,
            Long resourceId,
            String title,
            String summary,
            String content,
            String tags);

    void recordManual(
            Long resourceId,
            Long submitterId,
            Long reviewerId,
            Integer status,
            String reason);

    Page<ContentReviewRecord> pageRecords(
            int page,
            int size,
            String reviewType,
            String decision,
            Long resourceId);
}
