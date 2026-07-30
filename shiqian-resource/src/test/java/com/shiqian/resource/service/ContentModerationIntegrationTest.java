package com.shiqian.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.SensitiveWordDTO;
import com.shiqian.resource.entity.ContentReviewRecord;
import com.shiqian.resource.entity.SensitiveWord;
import com.shiqian.resource.mapper.ContentReviewRecordMapper;
import com.shiqian.resource.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentModerationIntegrationTest extends BaseResourceTest {

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    @Autowired
    private ContentReviewRecordMapper reviewRecordMapper;

    @BeforeEach
    void resetModerationData() {
        reviewRecordMapper.delete(new QueryWrapper<>());
        sensitiveWordMapper.delete(new QueryWrapper<>());
        sensitiveWordService.reload();
    }

    @AfterEach
    void restoreDefaultWords() {
        sensitiveWordService.create(word("违规", 2, 1), 0L);
        sensitiveWordService.create(word("敏感词", 2, 1), 0L);
        sensitiveWordService.create(word("广告", 2, 1), 0L);
    }

    @Test
    void databaseRuleShouldHotReloadAndRecordGenericAutoBlock() {
        SensitiveWordDTO dto = word("校验专用词", 3, 1);
        Long id = sensitiveWordService.create(dto, 2L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> contentReviewService.inspectOrReject(
                        7L,
                        null,
                        "普通标题",
                        "普通摘要",
                        "正文包含校验专用词",
                        "教程"));
        assertEquals("资源内容包含敏感词", error.getMessage());

        List<ContentReviewRecord> records = reviewRecordMapper.selectList(
                new QueryWrapper<ContentReviewRecord>().eq("review_type", "AUTO"));
        assertEquals(1, records.size());
        assertTrue(records.get(0).getMatchedWords().contains("校验专用词"));
        assertEquals("命中内容安全规则", records.get(0).getReason());

        sensitiveWordService.update(id, word("校验专用词", 3, 0));
        assertDoesNotThrow(() -> contentReviewService.inspectOrReject(
                7L,
                null,
                "校验专用词",
                null,
                null,
                null));
    }

    @Test
    void titleSummaryBodyAndTagsShouldUseSameDfaRules() {
        sensitiveWordService.create(word("四域规则", 2, 1), 2L);

        assertThrows(BusinessException.class, () ->
                contentReviewService.inspectOrReject(1L, null, "四域规则", null, null, null));
        assertThrows(BusinessException.class, () ->
                contentReviewService.inspectOrReject(1L, null, null, "四域规则", null, null));
        assertThrows(BusinessException.class, () ->
                contentReviewService.inspectOrReject(1L, null, null, null, "四域规则", null));
        assertThrows(BusinessException.class, () ->
                contentReviewService.inspectOrReject(1L, null, null, null, null, "四域规则"));

        assertEquals(4, reviewRecordMapper.selectCount(
                new QueryWrapper<ContentReviewRecord>().eq("review_type", "AUTO")));
    }

    @Test
    void manualReviewShouldBeDistinguishedFromAutomaticReview() {
        contentReviewService.recordManual(88L, 7L, 2L, 2, "请补充来源");

        ContentReviewRecord record = reviewRecordMapper.selectOne(
                new QueryWrapper<ContentReviewRecord>().eq("resource_id", 88L));
        assertEquals("MANUAL", record.getReviewType());
        assertEquals("NEEDS_CHANGES", record.getDecision());
        assertEquals("请补充来源", record.getReason());
    }

    private SensitiveWordDTO word(String value, int level, int status) {
        SensitiveWordDTO dto = new SensitiveWordDTO();
        dto.setWord(value);
        dto.setLevel(level);
        dto.setStatus(status);
        return dto;
    }
}
