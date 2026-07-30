package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.dto.JimengPromptItem;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.JimengIngestService;
import com.shiqian.resource.tools.JimengPromptImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 接收油猴脚本同步数据，幂等写入 CampusHub 图片频道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JimengIngestServiceImpl implements JimengIngestService {

    private static final String EXTERNAL_SOURCE = "JIMENG";
    private static final int STATUS_PUBLISHED = 1;
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    private final ResourceMapper resourceMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final OutboxService outboxService;

    @Value("${resource.upload-dir:uploads/resources}")
    private String uploadDir;

    @Value("${jimeng.ingest.user-id:1}")
    private long importUserId;

    @Value("${jimeng.ingest.allowed-image-hosts:p11-dreamina-sign.byteimg.com,p26-dreamina-sign.byteimg.com}")
    private String allowedImageHosts;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public List<String> findExistingWorkIds(Collection<String> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = workIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2000)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return resourceMapper.selectExistingExternalIdsIncludingDeleted(EXTERNAL_SOURCE, cleaned);
    }

    @Override
    @Transactional
    public Map<String, Object> ingestBatch(List<JimengPromptItem> items) {
        List<JimengPromptItem> batch = items == null ? List.of() : items;
        List<Map<String, Object>> results = new ArrayList<>(batch.size());
        int insertedOrUpdated = 0;
        int imagesStored = 0;

        for (JimengPromptItem item : batch) {
            Map<String, Object> row = new LinkedHashMap<>();
            String workId = trimToNull(item == null ? null : item.getWorkId());
            String prompt = trimToNull(item == null ? null : item.getPrompt());
            row.put("work_id", workId);
            if (workId == null || prompt == null) {
                row.put("ok", false);
                row.put("error", "缺少 work_id 或 prompt");
                results.add(row);
                continue;
            }
            boolean imageOk = upsertOne(item, workId, prompt);
            row.put("ok", true);
            row.put("image", imageOk);
            if (imageOk) {
                imagesStored++;
            }
            insertedOrUpdated++;
            results.add(row);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", results.stream().allMatch(item -> Boolean.TRUE.equals(item.get("ok"))));
        response.put("insertedOrUpdated", insertedOrUpdated);
        response.put("imagesStored", imagesStored);
        response.put("results", results);
        return response;
    }

    private boolean upsertOne(JimengPromptItem item, String workId, String prompt) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime sourceTime = resolveSourceTime(item);
        String title = JimengPromptImporter.buildDisplayTitle(prompt);
        String summary = JimengPromptImporter.buildDisplaySummary(
                item.getAuthor(), item.getModel(), item.getAspectRatio());
        String tags = buildTags(item);
        String contentMarkdown = JimengPromptImporter.normalizeWhitespace(prompt);

        Resource existing = resourceMapper.selectByExternalIdIncludingDeleted(
                EXTERNAL_SOURCE,
                workId);
        if (existing != null && Integer.valueOf(1).equals(existing.getDeleted())) {
            return false;
        }

        ImageStore image = storeImage(workId, item.getImageHigh(), item.getImageUrl());
        String contentType = image.available() ? "MIXED" : "ARTICLE";

        Resource resource;
        if (existing == null) {
            resource = new Resource();
            resource.setUserId(importUserId);
            resource.setExternalSource(EXTERNAL_SOURCE);
            resource.setExternalId(workId);
            resource.setDownloadCount(0);
            resource.setViewCount(0);
            resource.setVersion(1);
            resource.setStatus(STATUS_PUBLISHED);
            resource.setReviewReason("即梦油猴同步导入");
            resource.setReviewerId(importUserId);
            resource.setReviewTime(now);
            resource.setPublishedTime(sourceTime);
            resource.setCreateTime(sourceTime);
            resource.setUpdateTime(now);
            resource.setDeleted(0);
        } else {
            resource = existing;
            resource.setUpdateTime(now);
        }

        resource.setTitle(title);
        resource.setDescription(summary);
        resource.setSummary(summary);
        resource.setContentMarkdown(contentMarkdown);
        resource.setContentType(contentType);
        resource.setContentScene("GALLERY");
        resource.setTags(tags);

        if (image.available()) {
            resource.setFileUrl(image.fileUrl());
            resource.setFileSize(image.fileSize());
            resource.setFileType(image.extension());
        } else if (existing == null) {
            resource.setFileUrl(null);
            resource.setFileSize(0L);
            resource.setFileType(null);
        }

        boolean created = existing == null;
        if (created) {
            resourceMapper.insert(resource);
        } else {
            resourceMapper.updateById(resource);
        }

        if (image.available()) {
            upsertCover(resource.getId(), image, now);
        }
        outboxService.append(
                created ? OutboxEventType.RESOURCE_CREATED : OutboxEventType.RESOURCE_UPDATED,
                resource.getId(),
                ResourceEventPayload.resource(resource.getId()));
        return image.available();
    }

    private void upsertCover(Long resourceId, ImageStore image, LocalDateTime now) {
        ResourceAttachment cover = resourceAttachmentMapper.selectOne(new QueryWrapper<ResourceAttachment>()
                .eq("resource_id", resourceId)
                .eq("usage_type", "COVER")
                .orderByAsc("id")
                .last("LIMIT 1"));
        if (cover == null) {
            cover = new ResourceAttachment();
            cover.setResourceId(resourceId);
            cover.setUsageType("COVER");
            cover.setAssetKind("IMAGE");
            cover.setSortOrder(0);
            cover.setCreateTime(now);
            cover.setFileName(image.fileName());
            cover.setFileUrl(image.fileUrl());
            cover.setFileSize(image.fileSize());
            cover.setFileType(image.extension());
            cover.setMimeType(image.mimeType());
            resourceAttachmentMapper.insert(cover);
            return;
        }
        cover.setFileName(image.fileName());
        cover.setFileUrl(image.fileUrl());
        cover.setFileSize(image.fileSize());
        cover.setFileType(image.extension());
        cover.setMimeType(image.mimeType());
        cover.setAssetKind("IMAGE");
        cover.setSortOrder(0);
        resourceAttachmentMapper.updateById(cover);
    }

    private ImageStore storeImage(String workId, String imageHigh, String imageUrl) {
        Path userDir = Path.of(uploadDir).resolve(String.valueOf(importUserId)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(userDir);
        } catch (IOException error) {
            return ImageStore.missing("创建上传目录失败");
        }

        String fileStem = "jimeng-" + sha256(workId).substring(0, 32);
        ImageStore existing = findExistingImage(userDir, fileStem);
        if (existing.available()) {
            return existing;
        }

        Set<String> allowed = parseAllowedHosts();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(imageHigh)) {
            candidates.add(imageHigh.trim());
        }
        if (StringUtils.hasText(imageUrl)) {
            candidates.add(imageUrl.trim());
        }
        if (candidates.isEmpty()) {
            return ImageStore.missing("源记录没有图片");
        }

        String lastError = "图片不可用";
        for (String candidate : candidates) {
            try {
                return downloadCandidate(userDir, fileStem, candidate, allowed);
            } catch (Exception error) {
                lastError = safeMessage(error);
            }
        }
        return ImageStore.missing(lastError);
    }

    private ImageStore downloadCandidate(
            Path userDir,
            String fileStem,
            String rawUrl,
            Set<String> allowed) throws Exception {
        URI uri = JimengPromptImporter.validateImageUri(rawUrl, allowed);
        Long expiresAt = queryLong(uri, "x-expires");
        if (expiresAt != null && expiresAt <= Instant.now().getEpochSecond()) {
            throw new IOException("图片签名已过期");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 CampusHub-Jimeng-Ingest/1.0")
                .header("Referer", "https://jimeng.jianying.com/")
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200 && response.statusCode() != 206) {
                throw new IOException("图片服务返回 HTTP " + response.statusCode());
            }
            Path temporary = Files.createTempFile(userDir, fileStem + "-", ".part");
            long size = 0;
            try {
                try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        size += read;
                        if (size > MAX_IMAGE_BYTES) {
                            throw new IOException("图片超过大小限制");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                ImageType type = detectImageType(temporary);
                if (type == null) {
                    throw new IOException("图片文件头校验失败");
                }
                Path target = userDir.resolve(fileStem + "." + type.extension).normalize();
                if (!target.startsWith(userDir)) {
                    throw new IOException("图片保存路径越界");
                }
                try {
                    Files.move(temporary, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                String fileName = target.getFileName().toString();
                String fileUrl = "/api/resource/files/" + importUserId + "/" + fileName;
                return new ImageStore(true, fileName, fileUrl, size, type.extension, type.mimeType, null);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ImageStore findExistingImage(Path directory, String stem) {
        for (ImageType type : ImageType.values()) {
            Path file = directory.resolve(stem + "." + type.extension).normalize();
            try {
                if (file.startsWith(directory)
                        && Files.isRegularFile(file)
                        && Files.size(file) > 0
                        && detectImageType(file) == type) {
                    String name = file.getFileName().toString();
                    return new ImageStore(
                            true, name, "/api/resource/files/" + importUserId + "/" + name,
                            Files.size(file), type.extension, type.mimeType, null);
                }
            } catch (IOException ignored) {
                // 损坏文件会在本次同步中重新下载
            }
        }
        return ImageStore.missing("本地暂无图片");
    }

    private ImageType detectImageType(Path file) throws IOException {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = Files.newInputStream(file)) {
            read = input.read(header);
        }
        if (read >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageType.WEBP;
        }
        if (read >= 3
                && (header[0] & 0xff) == 0xff
                && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff) {
            return ImageType.JPEG;
        }
        if (read >= 8
                && (header[0] & 0xff) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return ImageType.PNG;
        }
        if (read >= 6) {
            String gif = new String(header, 0, 6);
            if ("GIF87a".equals(gif) || "GIF89a".equals(gif)) {
                return ImageType.GIF;
            }
        }
        return null;
    }

    private Set<String> parseAllowedHosts() {
        Set<String> hosts = new HashSet<>();
        for (String host : allowedImageHosts.split(",")) {
            String normalized = host.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                hosts.add(normalized);
            }
        }
        if (hosts.isEmpty()) {
            hosts.add("p11-dreamina-sign.byteimg.com");
            hosts.add("p26-dreamina-sign.byteimg.com");
        }
        return hosts;
    }

    private String buildTags(JimengPromptItem item) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("即梦");
        addTag(tags, item.getAuthor());
        addTag(tags, item.getModel());
        addTag(tags, item.getAspectRatio());
        return JimengPromptImporter.truncateCodePoints(String.join(", ", tags), 500);
    }

    private void addTag(Set<String> tags, String value) {
        String normalized = JimengPromptImporter.normalizeWhitespace(value);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        tags.add(JimengPromptImporter.truncateCodePoints(normalized.replace(',', '，'), 80));
    }

    private LocalDateTime resolveSourceTime(JimengPromptItem item) {
        if (item.getCreateTime() != null
                && item.getCreateTime() > 946684800L
                && item.getCreateTime() < 4102444800L) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(item.getCreateTime()), ZoneOffset.UTC);
        }
        if (StringUtils.hasText(item.getCollectedAt())) {
            try {
                return OffsetDateTime.parse(item.getCollectedAt()).toLocalDateTime();
            } catch (Exception ignored) {
                try {
                    return LocalDateTime.parse(item.getCollectedAt().replace(' ', 'T'));
                } catch (Exception ignoredAgain) {
                    // fall through
                }
            }
        }
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private Long queryLong(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0 && name.equalsIgnoreCase(part.substring(0, separator))) {
                try {
                    return Long.parseLong(part.substring(separator + 1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return JimengPromptImporter.truncateCodePoints(
                Objects.toString(current.getMessage(), current.getClass().getSimpleName())
                        .replaceAll("[\\r\\n\\t]", " "),
                180);
    }

    private enum ImageType {
        WEBP("webp", "image/webp"),
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif");

        private final String extension;
        private final String mimeType;

        ImageType(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    private record ImageStore(
            boolean available,
            String fileName,
            String fileUrl,
            long fileSize,
            String extension,
            String mimeType,
            String error) {

        static ImageStore missing(String error) {
            return new ImageStore(false, null, null, 0L, null, null, error);
        }
    }
}
