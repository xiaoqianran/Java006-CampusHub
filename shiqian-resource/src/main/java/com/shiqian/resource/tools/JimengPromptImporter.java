package com.shiqian.resource.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 即梦历史内容的一次性/可重复执行导入器。
 *
 * <p>安全边界：
 * <ul>
 *     <li>源库连接强制 readOnly，并在 READ ONLY 事务内只执行 SELECT。</li>
 *     <li>数据库凭据只从环境变量读取，不接受命令行明文参数。</li>
 *     <li>图片只允许 HTTPS 和明确的 CDN 域名，并拒绝解析到内网地址。</li>
 *     <li>图片限并发、限时、限大小，先写临时文件再原子替换。</li>
 *     <li>以 (external_source, external_id) 唯一键幂等写入。</li>
 * </ul>
 */
public final class JimengPromptImporter {

    private static final String EXTERNAL_SOURCE = "JIMENG";
    private static final int STATUS_PUBLISHED = 1;
    private static final int MAX_ERROR_DETAILS = 20;

    private static final String SOURCE_SQL = """
            SELECT id, work_id, prompt, author, model, create_time, collected_at,
                   image_url, image_high, aspect_ratio
            FROM jimeng_prompts
            WHERE id > ?
            ORDER BY id
            LIMIT ?
            """;

    private static final String UPSERT_RESOURCE_SQL = """
            INSERT INTO t_resource (
                user_id, title, description, summary, content_markdown,
                content_type, content_scene, tags, external_source, external_id,
                category_id, file_url, file_size, file_type,
                download_count, view_count, version, status,
                review_reason, reviewer_id, review_time, published_time,
                create_time, update_time, deleted
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, 'GALLERY', ?, ?, ?,
                NULL, ?, ?, ?,
                0, 0, 1, ?,
                ?, ?, ?, ?,
                ?, ?, 0
            )
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                description = VALUES(description),
                summary = VALUES(summary),
                content_markdown = VALUES(content_markdown),
                content_type = IF(
                    VALUES(file_url) IS NULL AND file_url IS NOT NULL,
                    'MIXED',
                    VALUES(content_type)
                ),
                content_scene = 'GALLERY',
                tags = VALUES(tags),
                file_url = COALESCE(VALUES(file_url), file_url),
                file_size = IF(VALUES(file_url) IS NULL, file_size, VALUES(file_size)),
                file_type = COALESCE(VALUES(file_type), file_type),
                update_time = VALUES(update_time)
            """;

    private static final String FIND_RESOURCE_SQL = """
            SELECT id
            FROM t_resource
            WHERE external_source = ? AND external_id = ?
            LIMIT 1
            """;

    private static final String FIND_COVER_SQL = """
            SELECT id
            FROM t_resource_attachment
            WHERE resource_id = ? AND usage_type = 'COVER'
            ORDER BY id
            LIMIT 1
            """;

    private static final String INSERT_COVER_SQL = """
            INSERT INTO t_resource_attachment (
                resource_id, file_name, file_url, file_size, file_type,
                mime_type, asset_kind, usage_type, sort_order, create_time
            ) VALUES (?, ?, ?, ?, ?, ?, 'IMAGE', 'COVER', 0, ?)
            """;

    private static final String UPDATE_COVER_SQL = """
            UPDATE t_resource_attachment
            SET file_name = ?, file_url = ?, file_size = ?, file_type = ?,
                mime_type = ?, asset_kind = 'IMAGE', sort_order = 0
            WHERE id = ?
            """;

    private JimengPromptImporter() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.from(args, System.getenv());
        Class.forName("com.mysql.cj.jdbc.Driver");

        List<SourceRow> rows = readSourceRows(config);
        System.out.printf(
                "即梦只读拉取：afterId=%d, limit=%d, fetched=%d, dryRun=%s, metadataOnly=%s%n",
                config.afterId(), config.limit(), rows.size(), config.dryRun(), config.metadataOnly());

        if (rows.isEmpty()) {
            return;
        }
        if (config.dryRun()) {
            long withPrompt = rows.stream().filter(row -> hasText(row.prompt())).count();
            long withAllowedImage = rows.stream()
                    .filter(row -> hasAllowedCandidate(row, config.allowedImageHosts()))
                    .count();
            System.out.printf(
                    "预演完成：有效提示词=%d，可尝试安全下载图片=%d；未写数据库、未下载文件。%n",
                    withPrompt, withAllowedImage);
            return;
        }

        Path userUploadPath = config.uploadRoot()
                .resolve(String.valueOf(config.importUserId()))
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(userUploadPath);
        if (!userUploadPath.startsWith(config.uploadRoot().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("导入用户目录越界");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        AtomicInteger downloadErrorLogs = new AtomicInteger();
        ExecutorService executor = config.metadataOnly()
                ? null
                : Executors.newFixedThreadPool(config.downloadConcurrency());
        List<Future<ImageResult>> downloads = new ArrayList<>(rows.size());
        if (executor != null) {
            for (SourceRow row : rows) {
                downloads.add(executor.submit(() ->
                        downloadImage(httpClient, config, userUploadPath, row, downloadErrorLogs)));
            }
        }

        int imported = 0;
        int failed = 0;
        int imagesStored = 0;
        int imagesMissing = 0;
        List<String> errors = new ArrayList<>();
        try (Connection target = DriverManager.getConnection(
                config.targetJdbcUrl(), config.targetUser(), config.targetPassword())) {
            target.setAutoCommit(false);
            for (int index = 0; index < rows.size(); index++) {
                SourceRow row = rows.get(index);
                ImageResult image = config.metadataOnly()
                        ? ImageResult.missing("metadata-only")
                        : awaitImage(downloads.get(index));
                if (image.available()) {
                    imagesStored++;
                } else {
                    imagesMissing++;
                }

                try {
                    importRow(target, config, row, image);
                    target.commit();
                    imported++;
                } catch (Exception error) {
                    rollbackQuietly(target);
                    failed++;
                    if (errors.size() < MAX_ERROR_DETAILS) {
                        errors.add(safeWorkId(row.workId()) + "：" + safeMessage(error));
                    }
                }
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        }

        System.out.printf(
                "导入完成：读取=%d，成功=%d，失败=%d，本地图片=%d，暂无图片=%d，lastSourceId=%d%n",
                rows.size(), imported, failed, imagesStored, imagesMissing,
                rows.get(rows.size() - 1).sourceRowId());
        errors.forEach(error -> System.err.println("导入失败：" + error));
        if (failed > 0) {
            throw new IllegalStateException("存在 " + failed + " 条导入失败，请修复后幂等重跑");
        }
    }

    private static List<SourceRow> readSourceRows(Config config) throws SQLException {
        List<SourceRow> rows = new ArrayList<>();
        try (Connection source = DriverManager.getConnection(
                config.sourceJdbcUrl(), config.sourceUser(), config.sourcePassword())) {
            source.setReadOnly(true);
            source.setAutoCommit(false);
            try (Statement statement = source.createStatement()) {
                statement.execute("SET TRANSACTION READ ONLY");
            }
            try (PreparedStatement statement = source.prepareStatement(SOURCE_SQL)) {
                statement.setLong(1, config.afterId());
                statement.setInt(2, config.limit());
                statement.setFetchSize(Math.min(config.limit(), 500));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String workId = trimToNull(resultSet.getString("work_id"));
                        String prompt = trimToNull(resultSet.getString("prompt"));
                        if (workId == null || prompt == null) {
                            continue;
                        }
                        rows.add(new SourceRow(
                                resultSet.getLong("id"),
                                workId,
                                prompt,
                                trimToNull(resultSet.getString("author")),
                                trimToNull(resultSet.getString("model")),
                                nullableLong(resultSet, "create_time"),
                                resultSet.getTimestamp("collected_at"),
                                trimToNull(resultSet.getString("image_url")),
                                trimToNull(resultSet.getString("image_high")),
                                trimToNull(resultSet.getString("aspect_ratio"))));
                    }
                }
            } finally {
                source.rollback();
            }
        }
        return rows;
    }

    private static void importRow(
            Connection target,
            Config config,
            SourceRow row,
            ImageResult image) throws SQLException {
        LocalDateTime sourceTime = resolveSourceTime(row);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // 图片频道：标题短展示，摘要不重复正文提示词，正文只保留一次完整 prompt
        String title = buildDisplayTitle(row.prompt());
        String summary = buildDisplaySummary(row.author(), row.model(), row.aspectRatio());
        String tags = buildTags(row);
        String contentType = image.available() ? "MIXED" : "ARTICLE";
        String contentMarkdown = normalizeWhitespace(row.prompt());

        try (PreparedStatement statement = target.prepareStatement(UPSERT_RESOURCE_SQL)) {
            int parameter = 1;
            statement.setLong(parameter++, config.importUserId());
            statement.setString(parameter++, title);
            statement.setString(parameter++, summary);
            statement.setString(parameter++, summary);
            statement.setString(parameter++, contentMarkdown);
            statement.setString(parameter++, contentType);
            statement.setString(parameter++, tags);
            statement.setString(parameter++, EXTERNAL_SOURCE);
            statement.setString(parameter++, row.workId());
            statement.setString(parameter++, image.fileUrl());
            statement.setLong(parameter++, image.available() ? image.fileSize() : 0L);
            statement.setString(parameter++, image.mimeType());
            statement.setInt(parameter++, STATUS_PUBLISHED);
            statement.setString(parameter++, "即梦历史内容安全导入");
            statement.setLong(parameter++, config.importUserId());
            statement.setTimestamp(parameter++, Timestamp.valueOf(now));
            statement.setTimestamp(parameter++, Timestamp.valueOf(sourceTime));
            statement.setTimestamp(parameter++, Timestamp.valueOf(sourceTime));
            statement.setTimestamp(parameter, Timestamp.valueOf(now));
            statement.executeUpdate();
        }

        long resourceId;
        try (PreparedStatement statement = target.prepareStatement(FIND_RESOURCE_SQL)) {
            statement.setString(1, EXTERNAL_SOURCE);
            statement.setString(2, row.workId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("无法读取导入后的内容ID");
                }
                resourceId = resultSet.getLong("id");
            }
        }

        if (image.available()) {
            upsertCover(target, resourceId, image, now);
        }
    }

    private static void upsertCover(
            Connection target,
            long resourceId,
            ImageResult image,
            LocalDateTime now) throws SQLException {
        Long attachmentId = null;
        try (PreparedStatement statement = target.prepareStatement(FIND_COVER_SQL)) {
            statement.setLong(1, resourceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    attachmentId = resultSet.getLong("id");
                }
            }
        }

        if (attachmentId == null) {
            try (PreparedStatement statement = target.prepareStatement(INSERT_COVER_SQL)) {
                statement.setLong(1, resourceId);
                statement.setString(2, image.fileName());
                statement.setString(3, image.fileUrl());
                statement.setLong(4, image.fileSize());
                statement.setString(5, image.extension());
                statement.setString(6, image.mimeType());
                statement.setTimestamp(7, Timestamp.valueOf(now));
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = target.prepareStatement(UPDATE_COVER_SQL)) {
            statement.setString(1, image.fileName());
            statement.setString(2, image.fileUrl());
            statement.setLong(3, image.fileSize());
            statement.setString(4, image.extension());
            statement.setString(5, image.mimeType());
            statement.setLong(6, attachmentId);
            statement.executeUpdate();
        }
    }

    private static ImageResult awaitImage(Future<ImageResult> future) {
        try {
            return future.get();
        } catch (Exception error) {
            return ImageResult.missing(safeMessage(error));
        }
    }

    private static ImageResult downloadImage(
            HttpClient client,
            Config config,
            Path userUploadPath,
            SourceRow row,
            AtomicInteger errorLogs) {
        String fileStem = "jimeng-" + sha256(row.workId()).substring(0, 32);
        ImageResult existing = findExistingImage(userUploadPath, config.importUserId(), fileStem);
        if (existing.available()) {
            return existing;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (hasText(row.imageHigh())) {
            candidates.add(row.imageHigh());
        }
        if (hasText(row.imageUrl())) {
            candidates.add(row.imageUrl());
        }
        if (candidates.isEmpty()) {
            return ImageResult.missing("源记录没有图片");
        }

        String lastError = "图片不可用";
        for (String candidate : candidates) {
            try {
                return downloadCandidate(client, config, userUploadPath, fileStem, candidate);
            } catch (Exception error) {
                lastError = safeMessage(error);
            }
        }
        if (errorLogs.incrementAndGet() <= MAX_ERROR_DETAILS) {
            System.err.printf("图片暂未拉取：workId=%s，原因=%s%n", safeWorkId(row.workId()), lastError);
        }
        return ImageResult.missing(lastError);
    }

    private static ImageResult downloadCandidate(
            HttpClient client,
            Config config,
            Path userUploadPath,
            String fileStem,
            String rawUrl) throws Exception {
        URI uri = validateImageUri(rawUrl, config.allowedImageHosts());
        Long expiresAt = queryLong(uri, "x-expires");
        if (expiresAt != null && expiresAt <= Instant.now().getEpochSecond()) {
            throw new IOException("图片签名已过期");
        }
        if (userUploadPath.toFile().getUsableSpace() < config.minimumFreeBytes()) {
            throw new IOException("磁盘剩余空间低于安全阈值");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "Mozilla/5.0 CampusHub-Jimeng-Importer/1.0")
                .header("Referer", "https://jimeng.jianying.com/")
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200 && response.statusCode() != 206) {
                throw new IOException("图片服务返回 HTTP " + response.statusCode());
            }
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .split(";", 2)[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("image/")) {
                throw new IOException("响应不是图片");
            }
            long declaredLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1L);
            if (declaredLength > config.maxImageBytes()) {
                throw new IOException("图片超过大小限制");
            }

            Path temporary = Files.createTempFile(userUploadPath, fileStem + "-", ".part");
            long size = 0;
            try {
                try (OutputStream output = Files.newOutputStream(
                        temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        size += read;
                        if (size > config.maxImageBytes()) {
                            throw new IOException("图片超过大小限制");
                        }
                        output.write(buffer, 0, read);
                    }
                }

                ImageType imageType = detectImageType(temporary);
                if (imageType == null) {
                    throw new IOException("图片文件头校验失败");
                }
                Path target = userUploadPath.resolve(fileStem + "." + imageType.extension()).normalize();
                if (!target.startsWith(userUploadPath)) {
                    throw new IOException("图片保存路径越界");
                }
                moveAtomically(temporary, target);
                String fileName = target.getFileName().toString();
                String fileUrl = "/api/resource/files/" + config.importUserId() + "/" + fileName;
                return new ImageResult(
                        true, fileName, fileUrl, size,
                        imageType.extension(), imageType.mimeType(), null);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static URI validateImageUri(String rawUrl, Set<String> allowedHosts) throws Exception {
        URI uri = URI.create(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("图片地址必须使用 HTTPS 且不能包含用户信息");
        }
        String host = uri.getHost();
        if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("图片域名不在允许列表");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException("图片域名解析到非公网地址");
            }
        }
        return uri;
    }

    private static ImageResult findExistingImage(Path directory, long userId, String stem) {
        for (ImageType type : ImageType.values()) {
            Path file = directory.resolve(stem + "." + type.extension()).normalize();
            try {
                if (file.startsWith(directory)
                        && Files.isRegularFile(file)
                        && Files.size(file) > 0
                        && detectImageType(file) == type) {
                    String name = file.getFileName().toString();
                    return new ImageResult(
                            true, name, "/api/resource/files/" + userId + "/" + name,
                            Files.size(file), type.extension(), type.mimeType(), null);
                }
            } catch (IOException ignored) {
                // 损坏文件会在本次导入中重新下载。
            }
        }
        return ImageResult.missing("本地暂无图片");
    }

    private static ImageType detectImageType(Path file) throws IOException {
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
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && (header[4] & 0xff) == 0x0d
                && (header[5] & 0xff) == 0x0a
                && (header[6] & 0xff) == 0x1a
                && (header[7] & 0xff) == 0x0a) {
            return ImageType.PNG;
        }
        if (read >= 6) {
            String gif = new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(gif) || "GIF89a".equals(gif)) {
                return ImageType.GIF;
            }
        }
        return null;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public static String truncateCodePoints(String value, int maxCodePoints) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, Math.max(1, maxCodePoints - 1));
        return value.substring(0, end) + "…";
    }

    /**
     * 画廊卡片标题：截断提示词，避免与正文/摘要三处重复堆满整页。
     */
    public static String buildDisplayTitle(String prompt) {
        String normalized = normalizeWhitespace(prompt);
        if (normalized.isEmpty()) {
            return "即梦作品";
        }
        return truncateCodePoints(normalized, 42);
    }

    /**
     * 画廊摘要：作者/模型等元信息，不再回填完整提示词。
     */
    public static String buildDisplaySummary(String author, String model, String aspectRatio) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (hasText(author)) {
            parts.add(normalizeWhitespace(author));
        }
        if (hasText(model)) {
            parts.add(normalizeWhitespace(model));
        }
        if (hasText(aspectRatio)) {
            parts.add(normalizeWhitespace(aspectRatio));
        }
        if (parts.isEmpty()) {
            return "即梦 AI 作品";
        }
        return truncateCodePoints("即梦 · " + String.join(" · ", parts), 120);
    }

    private static String buildTags(SourceRow row) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("即梦");
        addTag(tags, row.author());
        addTag(tags, row.model());
        if (hasText(row.aspectRatio())) {
            addTag(tags, row.aspectRatio());
        }
        return truncateCodePoints(String.join(", ", tags), 500);
    }

    private static void addTag(Set<String> tags, String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized.isEmpty()) {
            return;
        }
        normalized = normalized.replace(',', '，');
        tags.add(truncateCodePoints(normalized, 80));
    }

    private static LocalDateTime resolveSourceTime(SourceRow row) {
        if (row.createTimeEpoch() != null
                && row.createTimeEpoch() > 946684800L
                && row.createTimeEpoch() < 4102444800L) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(row.createTimeEpoch()), ZoneOffset.UTC);
        }
        if (row.collectedAt() != null) {
            return row.collectedAt().toLocalDateTime();
        }
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static boolean hasAllowedCandidate(SourceRow row, Set<String> allowedHosts) {
        return Arrays.asList(row.imageHigh(), row.imageUrl()).stream()
                .filter(JimengPromptImporter::hasText)
                .anyMatch(value -> {
                    try {
                        URI uri = URI.create(value);
                        return "https".equalsIgnoreCase(uri.getScheme())
                                && uri.getHost() != null
                                && allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
                    } catch (RuntimeException ignored) {
                        return false;
                    }
                });
    }

    private static Long queryLong(URI uri, String name) {
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

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String sha256(String value) {
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

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 保留原始异常。
        }
    }

    private static String safeWorkId(String workId) {
        return truncateCodePoints(
                Objects.toString(workId, "(missing)").replaceAll("[\\r\\n\\t]", "_"),
                80);
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return truncateCodePoints(
                Objects.toString(current.getMessage(), current.getClass().getSimpleName())
                        .replaceAll("[\\r\\n\\t]", " "),
                180);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

        String extension() {
            return extension;
        }

        String mimeType() {
            return mimeType;
        }
    }

    private record SourceRow(
            long sourceRowId,
            String workId,
            String prompt,
            String author,
            String model,
            Long createTimeEpoch,
            Timestamp collectedAt,
            String imageUrl,
            String imageHigh,
            String aspectRatio) {
    }

    private record ImageResult(
            boolean available,
            String fileName,
            String fileUrl,
            long fileSize,
            String extension,
            String mimeType,
            String error) {

        static ImageResult missing(String error) {
            return new ImageResult(false, null, null, 0L, null, null, error);
        }
    }

    record Config(
            String sourceJdbcUrl,
            String sourceUser,
            String sourcePassword,
            String targetJdbcUrl,
            String targetUser,
            String targetPassword,
            long importUserId,
            Path uploadRoot,
            Set<String> allowedImageHosts,
            int limit,
            long afterId,
            boolean dryRun,
            boolean metadataOnly,
            int downloadConcurrency,
            long maxImageBytes,
            long minimumFreeBytes) {

        static Config from(String[] args, java.util.Map<String, String> env) {
            int limit = 100;
            long afterId = 0L;
            boolean dryRun = false;
            boolean metadataOnly = false;
            for (String arg : args) {
                if (arg.startsWith("--limit=")) {
                    limit = parseInt(arg.substring("--limit=".length()), "limit");
                } else if (arg.startsWith("--after-id=")) {
                    afterId = parseLong(arg.substring("--after-id=".length()), "after-id");
                } else if ("--dry-run".equals(arg)) {
                    dryRun = true;
                } else if ("--metadata-only".equals(arg)) {
                    metadataOnly = true;
                } else {
                    throw new IllegalArgumentException("不支持的参数：" + arg);
                }
            }
            if (limit < 1 || limit > 20000) {
                throw new IllegalArgumentException("limit 必须在 1 到 20000 之间");
            }
            if (afterId < 0) {
                throw new IllegalArgumentException("after-id 不能小于 0");
            }

            String sourceHost = required(env, "JIMENG_DB_HOST");
            int sourcePort = envInt(env, "JIMENG_DB_PORT", 3306, 1, 65535);
            String sourceDatabase = required(env, "JIMENG_DB_NAME");
            String sourceUser = required(env, "JIMENG_DB_USER");
            String sourcePassword = required(env, "JIMENG_DB_PASSWORD");

            String targetHost = env.getOrDefault("CAMPUSHUB_DB_HOST", "127.0.0.1");
            int targetPort = envInt(env, "CAMPUSHUB_DB_PORT", 3306, 1, 65535);
            String targetDatabase = env.getOrDefault("CAMPUSHUB_DB_NAME", "shiqian_resource");
            String targetUser = env.getOrDefault("CAMPUSHUB_DB_USER", "root");
            String targetPassword = required(env, "CAMPUSHUB_DB_PASSWORD");

            Set<String> allowedHosts = new HashSet<>();
            String rawHosts = env.getOrDefault(
                    "JIMENG_ALLOWED_IMAGE_HOSTS",
                    "p11-dreamina-sign.byteimg.com,p26-dreamina-sign.byteimg.com");
            for (String host : rawHosts.split(",")) {
                String normalized = host.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    allowedHosts.add(normalized);
                }
            }
            if (allowedHosts.isEmpty()) {
                throw new IllegalArgumentException("图片域名允许列表不能为空");
            }

            long userId = envLong(env, "JIMENG_IMPORT_USER_ID", 1L, 1L, Long.MAX_VALUE);
            int concurrency = envInt(env, "JIMENG_DOWNLOAD_CONCURRENCY", 4, 1, 8);
            long maxImageBytes = envLong(
                    env, "JIMENG_MAX_IMAGE_BYTES", 20L * 1024 * 1024, 1024, 100L * 1024 * 1024);
            long minimumFreeBytes = envLong(
                    env, "JIMENG_MIN_FREE_BYTES", 512L * 1024 * 1024, 0, Long.MAX_VALUE);
            Path uploadRoot = Path.of(
                    env.getOrDefault("CAMPUSHUB_UPLOAD_ROOT", "uploads/resources"));

            return new Config(
                    jdbcUrl(sourceHost, sourcePort, sourceDatabase),
                    sourceUser,
                    sourcePassword,
                    jdbcUrl(targetHost, targetPort, targetDatabase),
                    targetUser,
                    targetPassword,
                    userId,
                    uploadRoot,
                    Set.copyOf(allowedHosts),
                    limit,
                    afterId,
                    dryRun,
                    metadataOnly,
                    concurrency,
                    maxImageBytes,
                    minimumFreeBytes);
        }

        private static String jdbcUrl(String host, int port, String database) {
            if (!host.matches("[A-Za-z0-9._:-]+")) {
                throw new IllegalArgumentException("数据库主机格式不合法");
            }
            if (!database.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("数据库名称格式不合法");
            }
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=UTC&connectTimeout=10000&socketTimeout=60000"
                    + "&allowMultiQueries=false";
        }

        private static String required(java.util.Map<String, String> env, String name) {
            String value = trimToNull(env.get(name));
            if (value == null) {
                throw new IllegalArgumentException("缺少环境变量：" + name);
            }
            return value;
        }

        private static int envInt(
                java.util.Map<String, String> env,
                String name,
                int fallback,
                int min,
                int max) {
            String raw = trimToNull(env.get(name));
            int value = raw == null ? fallback : parseInt(raw, name);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " 超出允许范围");
            }
            return value;
        }

        private static long envLong(
                java.util.Map<String, String> env,
                String name,
                long fallback,
                long min,
                long max) {
            String raw = trimToNull(env.get(name));
            long value = raw == null ? fallback : parseLong(raw, name);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " 超出允许范围");
            }
            return value;
        }

        private static int parseInt(String value, String name) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(name + " 必须是整数", error);
            }
        }

        private static long parseLong(String value, String name) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(name + " 必须是整数", error);
            }
        }
    }
}
