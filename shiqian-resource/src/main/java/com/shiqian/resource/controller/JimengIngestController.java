package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.dto.JimengBatchRequest;
import com.shiqian.resource.dto.JimengExistingRequest;
import com.shiqian.resource.dto.JimengPromptItem;
import com.shiqian.resource.service.JimengIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兼容油猴「即梦提示词收集器 MySQL同步版」的同步接口。
 * 仅允许本机回环访问，避免公网裸写。
 */
@Tag(name = "即梦同步", description = "油猴脚本批量同步即梦图片与提示词")
@RestController
@RequestMapping("/api/jimeng/prompts")
@RequiredArgsConstructor
public class JimengIngestController {

    private static final int MAX_BATCH_SIZE = 2000;

    private final JimengIngestService jimengIngestService;
    private final ObjectMapper objectMapper;

    @Value("${jimeng.ingest.token:}")
    private String ingestToken;

    @Operation(summary = "批量同步（JSON items）")
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> batch(
            HttpServletRequest request,
            @RequestBody JimengBatchRequest body) {
        assertAuthorized(request);
        List<JimengPromptItem> items = body == null || body.getItems() == null
                ? List.of()
                : body.getItems();
        assertBatchSize(items.size());
        return jimengIngestService.ingestBatch(items);
    }

    @Operation(summary = "流式同步（NDJSON，每行一条）")
    @PostMapping(value = "/stream", consumes = {
            "application/x-ndjson",
            "application/x-ndjson;charset=utf-8",
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.ALL_VALUE
    })
    public Map<String, Object> stream(HttpServletRequest request) throws Exception {
        assertAuthorized(request);
        List<JimengPromptItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!StringUtils.hasText(trimmed)) {
                    continue;
                }
                assertBatchSize(items.size() + 1);
                items.add(objectMapper.readValue(trimmed, JimengPromptItem.class));
            }
        }
        return jimengIngestService.ingestBatch(items);
    }

    @Operation(summary = "查询已存在 work_id，避免重复全量上传")
    @PostMapping(value = "/existing", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> existing(
            HttpServletRequest request,
            @RequestBody JimengExistingRequest body) {
        assertAuthorized(request);
        List<String> workIds = body == null || body.getWorkIds() == null
                ? List.of()
                : body.getWorkIds();
        assertBatchSize(workIds.size());
        List<String> existingWorkIds = jimengIngestService.findExistingWorkIds(workIds);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("existingWorkIds", existingWorkIds);
        response.put("checked", workIds.size());
        response.put("existing", existingWorkIds.size());
        return response;
    }

    private void assertAuthorized(HttpServletRequest request) {
        if (!StringUtils.hasText(ingestToken) || ingestToken.length() < 32) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "即梦同步接口未配置");
        }
        String suppliedToken = request.getHeader("X-Jimeng-Sync-Token");
        if (!constantTimeEquals(ingestToken, suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "即梦同步令牌无效");
        }

        // 经网关/反代的请求带有转发头：即使 peer 是 loopback 也一律拒绝。
        if (StringUtils.hasText(request.getHeader("X-Forwarded-For"))
                || StringUtils.hasText(request.getHeader("X-Real-IP"))
                || StringUtils.hasText(request.getHeader("Forwarded"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "即梦同步接口仅允许本机直连");
        }
        // 仅信任直连对端，禁止用可伪造的 X-Forwarded-For 冒充本机。
        if (com.shiqian.common.security.ClientIpResolver.isDirectLoopback(request)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "即梦同步接口仅允许本机访问");
    }

    private void assertBatchSize(int size) {
        if (size > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "单次最多同步 " + MAX_BATCH_SIZE + " 条");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        int difference = left.length ^ right.length;
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            byte leftByte = index < left.length ? left[index] : 0;
            byte rightByte = index < right.length ? right[index] : 0;
            difference |= leftByte ^ rightByte;
        }
        return difference == 0;
    }
}
