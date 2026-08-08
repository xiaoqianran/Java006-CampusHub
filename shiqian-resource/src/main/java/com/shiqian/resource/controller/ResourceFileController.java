package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.common.ratelimit.DistributedRateLimit;
import com.shiqian.common.ratelimit.RateLimitKeyMode;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.config.ResourceStorageProperties;
import com.shiqian.resource.dto.ArchivePreviewVO;
import com.shiqian.resource.dto.FileUploadVO;
import com.shiqian.resource.dto.SignedFileUrlVO;
import com.shiqian.resource.dto.TextFilePreviewVO;
import com.shiqian.resource.entity.StoredObject;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.monitoring.ResourceBusinessMetrics;
import com.shiqian.resource.service.StoredObjectService;
import com.shiqian.resource.storage.StoredObjectAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Tag(name = "资源文件", description = "资源附件上传与私有对象访问")
@Slf4j
@RestController
@RequestMapping("/api/resource/files")
@RequiredArgsConstructor
public class ResourceFileController {

    private static final Set<String> TEXT_PREVIEW_EXTENSIONS = Set.of(
            "txt", "md", "java", "py", "js", "ts", "vue", "c", "cpp", "h",
            "go", "rs", "sql", "json", "xml", "yaml", "yml", "html", "css", "sh");

    private final StoredObjectService storedObjectService;
    private final ResourceStorageProperties storageProperties;
    private final ResourceBusinessMetrics businessMetrics;
    private final ResourceMapper resourceMapper;

    @Value("${resource.upload-dir:uploads/resources}")
    private String legacyUploadDir;

    @Value("${resource.upload.max-files-per-request:10}")
    private int maxFilesPerRequest;

    @Value("${resource.upload.max-text-preview-bytes:524288}")
    private int maxTextPreviewBytes;

    @Value("${resource.upload.max-archive-preview-entries:500}")
    private int maxArchiveEntries;

    @Operation(summary = "批量上传资源附件")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:create')")
    @DistributedRateLimit(name = "resource:upload", limit = 20, windowSeconds = 60, keyMode = RateLimitKeyMode.USER)
    public Result<List<FileUploadVO>> uploadFiles(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        List<MultipartFile> uploadFiles = new ArrayList<>();
        if (files != null) uploadFiles.addAll(files);
        if (file != null) uploadFiles.add(file);
        List<MultipartFile> nonEmptyFiles = uploadFiles.stream()
                .filter(item -> item != null && !item.isEmpty())
                .toList();
        if (uploadFiles.isEmpty()) {
            return Result.fail(400, "请选择要上传的文件");
        }
        if (nonEmptyFiles.isEmpty()) {
            return Result.fail(400, "不能上传空文件");
        }
        if (nonEmptyFiles.size() > maxFilesPerRequest) {
            return Result.fail(400, "单次最多上传" + maxFilesPerRequest + "个文件");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        try {
            return Result.ok(storedObjectService.storeFiles(userId, nonEmptyFiles));
        } catch (RuntimeException error) {
            businessMetrics.uploadFailed();
            throw error;
        }
    }

    @Operation(summary = "获取 MinIO 私有文件临时签名地址")
    @GetMapping("/object/{publicId}/signed-url")
    public Result<SignedFileUrlVO> signedUrl(
            @PathVariable String publicId,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        String url = storedObjectService.createSignedUrl(
                        publicId,
                        SecurityUtil.getCurrentUserId(),
                        SecurityUtil.hasAuthority("resource:audit"),
                        inline)
                .orElse("/api/resource/files/object/" + publicId + "?inline=" + inline);
        return Result.ok(new SignedFileUrlVO(
                url,
                LocalDateTime.now().plus(storageProperties.getSignedUrlTtl())));
    }

    @Operation(summary = "访问统一存储中的资源附件")
    @GetMapping("/object/{publicId}")
    public ResponseEntity<?> getStoredObject(
            @PathVariable String publicId,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        Long requesterId = SecurityUtil.getCurrentUserId();
        boolean privileged = SecurityUtil.hasAuthority("resource:audit");
        var signedUrl = storedObjectService.createSignedUrl(
                publicId, requesterId, privileged, inline);
        if (signedUrl.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(signedUrl.get()))
                    .build();
        }
        StoredObjectAccess access = storedObjectService.open(
                publicId, requesterId, privileged);
        StoredObject metadata = access.metadata();
        return streamResponse(
                access.inputStream(),
                metadata.getFileSize(),
                metadata.getMimeType(),
                metadata.getOriginalName(),
                inline);
    }

    @Operation(summary = "预览文本附件")
    @GetMapping("/preview/text")
    public Result<TextFilePreviewVO> previewText(
            @RequestParam("path") String relativePath) throws IOException {
        try (PreviewSource source = resolvePreviewSource(relativePath)) {
            if (source == null) {
                return Result.fail(404, "预览文件不存在");
            }
            if (!TEXT_PREVIEW_EXTENSIONS.contains(source.extension())) {
                return Result.fail(400, "该文件不支持文本预览");
            }
            int limit = Math.max(1024, maxTextPreviewBytes);
            byte[] bytes = source.inputStream().readNBytes(limit + 1);
            boolean truncated = bytes.length > limit;
            int contentLength = Math.min(bytes.length, limit);
            String content = new String(bytes, 0, contentLength, StandardCharsets.UTF_8);
            return Result.ok(new TextFilePreviewVO(content, truncated, source.size()));
        }
    }

    @Operation(summary = "预览 ZIP 附件目录")
    @GetMapping("/preview/archive")
    public Result<ArchivePreviewVO> previewArchive(
            @RequestParam("path") String relativePath) throws IOException {
        PreviewSource source = resolvePreviewSource(relativePath);
        if (source == null) {
            return Result.fail(404, "预览文件不存在");
        }
        if (!"zip".equals(source.extension())) {
            source.close();
            return Result.fail(400, "当前仅支持预览 ZIP 压缩包目录");
        }

        int limit = Math.max(1, maxArchiveEntries);
        List<ArchivePreviewVO.Entry> entries = new ArrayList<>();
        int totalEntries = 0;
        boolean truncated = false;
        try (source; ZipInputStream zip = new ZipInputStream(source.inputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                totalEntries++;
                if (entries.size() < limit) {
                    String name = entry.getName();
                    if (name.length() > 500) {
                        name = name.substring(0, 497) + "...";
                    }
                    entries.add(new ArchivePreviewVO.Entry(
                            name,
                            entry.isDirectory(),
                            entry.getSize(),
                            entry.getCompressedSize()));
                } else {
                    truncated = true;
                }
                zip.closeEntry();
            }
        } catch (ZipException error) {
            return Result.fail(400, "ZIP 文件已损坏或格式不正确");
        }
        return Result.ok(new ArchivePreviewVO(entries, totalEntries, truncated));
    }

    /**
     * 兼容历史本地文件 URL；新上传文件不会再暴露用户目录或磁盘路径。
     * 访问控制：已发布资源引用 / 文件所有者目录 / 审核员。
     */
    @Operation(summary = "访问历史本地资源附件")
    @GetMapping("/**")
    public ResponseEntity<org.springframework.core.io.Resource> getLegacyFile(
            HttpServletRequest request,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) throws IOException {
        String prefix = "/api/resource/files/";
        String uri = request.getRequestURI();
        String filename = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
        filename = UriUtils.decode(filename, StandardCharsets.UTF_8);
        if (filename.startsWith("object/")
                || filename.startsWith("preview/")) {
            return ResponseEntity.notFound().build();
        }
        Path target = resolveLegacyFile(filename);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessLegacyRelativePath(filename)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        UrlResource resource = new UrlResource(target.toUri());
        MediaType mediaType = MediaTypeFactory.getMediaType(target.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(target))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(target.getFileName().toString(), inline))
                .body(resource);
    }

    private PreviewSource resolvePreviewSource(String relativePath) throws IOException {
        if (StringUtils.hasText(relativePath) && relativePath.startsWith("object/")) {
            String publicId = relativePath.substring("object/".length());
            if (publicId.contains("/")) {
                return null;
            }
            StoredObjectAccess access = storedObjectService.open(
                    publicId,
                    SecurityUtil.getCurrentUserId(),
                    SecurityUtil.hasAuthority("resource:audit"));
            StoredObject metadata = access.metadata();
            return new PreviewSource(
                    access.inputStream(),
                    metadata.getFileSize() == null ? 0L : metadata.getFileSize(),
                    metadata.getExtension());
        }
        if (!canAccessLegacyRelativePath(relativePath)) {
            return null;
        }
        Path target = resolveLegacyFile(relativePath);
        if (target == null) {
            return null;
        }
        return new PreviewSource(
                Files.newInputStream(target),
                Files.size(target),
                fileExtension(target.getFileName().toString()));
    }

    private Path resolveLegacyFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)
                || relativePath.contains("..")
                || relativePath.contains("\\")
                || relativePath.startsWith("/")) {
            return null;
        }
        Path root = Path.of(legacyUploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return null;
        }
        return target;
    }

    /**
     * 历史路径形态多为 {userId}/filename；仅所有者、审核员或已发布资源引用可访问。
     */
    private boolean canAccessLegacyRelativePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return false;
        }
        if (SecurityUtil.hasAuthority("resource:audit")) {
            return true;
        }
        Long userId = SecurityUtil.getCurrentUserId();
        int slash = relativePath.indexOf('/');
        if (userId != null && slash > 0) {
            String ownerSegment = relativePath.substring(0, slash);
            if (String.valueOf(userId).equals(ownerSegment)) {
                return true;
            }
        }
        String fileUrl = "/api/resource/files/" + relativePath;
        return resourceMapper.isPublishedFileUrl(fileUrl);
    }

    private String fileExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0
                ? name.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private ResponseEntity<InputStreamResource> streamResponse(
            InputStream inputStream,
            Long size,
            String mimeType,
            String originalName,
            boolean inline) {
        MediaType mediaType;
        try {
            mediaType = StringUtils.hasText(mimeType)
                    ? MediaType.parseMediaType(mimeType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(originalName, inline));
        if (size != null && size >= 0) {
            response.contentLength(size);
        }
        return response.body(new InputStreamResource(inputStream));
    }

    private String contentDisposition(String fileName, boolean inline) {
        return (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private record PreviewSource(InputStream inputStream, long size, String extension)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
