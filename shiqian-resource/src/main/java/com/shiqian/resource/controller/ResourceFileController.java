package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.common.security.SecurityUtil;
import com.shiqian.resource.dto.FileUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Tag(name = "资源文件", description = "资源附件上传与访问")
@Slf4j
@RestController
@RequestMapping("/api/resource/files")
public class ResourceFileController {

    private final Path uploadPath;
    private final long maxFileSize;
    private final int maxFilesPerRequest;
    private final long maxUserStorage;
    private final Set<String> allowedExtensions;
    private final Semaphore uploadSlots;
    private final ConcurrentMap<Long, AtomicLong> userStorageUsage = new ConcurrentHashMap<>();

    public ResourceFileController(
            @Value("${resource.upload-dir:uploads/resources}") String uploadDir,
            @Value("${resource.upload.max-file-size:52428800}") long maxFileSize,
            @Value("${resource.upload.max-files-per-request:10}") int maxFilesPerRequest,
            @Value("${resource.upload.max-user-storage:1073741824}") long maxUserStorage,
            @Value("${resource.upload.max-concurrent-files:16}") int maxConcurrentFiles,
            @Value("${resource.upload.allowed-extensions:pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,jpg,jpeg,png,gif,zip,rar,7z,java,py,js,ts,vue,c,cpp,h,go,rs,sql,json,xml,yaml,yml,html,css,sh}")
            String allowedExtensions) {
        this.uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.maxFilesPerRequest = maxFilesPerRequest;
        this.maxUserStorage = maxUserStorage;
        this.uploadSlots = new Semaphore(Math.max(1, maxConcurrentFiles), true);
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Operation(summary = "批量上传资源附件")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:create')")
    public Result<List<FileUploadVO>> uploadFiles(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        List<MultipartFile> uploadFiles = new ArrayList<>();
        if (files != null) {
            uploadFiles.addAll(files);
        }
        if (file != null) {
            uploadFiles.add(file);
        }

        if (uploadFiles.isEmpty()) {
            return Result.fail(400, "请选择要上传的文件");
        }

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }

        List<MultipartFile> nonEmptyFiles = uploadFiles.stream()
                .filter(item -> item != null && !item.isEmpty())
                .toList();
        if (nonEmptyFiles.isEmpty()) {
            return Result.fail(400, "不能上传空文件");
        }
        if (nonEmptyFiles.size() > maxFilesPerRequest) {
            return Result.fail(400, "单次最多上传" + maxFilesPerRequest + "个文件");
        }

        for (MultipartFile uploadFile : nonEmptyFiles) {
            String validationError = validateFile(uploadFile);
            if (validationError != null) {
                return Result.fail(400, validationError);
            }
        }

        int permits = nonEmptyFiles.size();
        if (!uploadSlots.tryAcquire(permits)) {
            return Result.fail(429, "当前上传任务较多，请稍后重试");
        }
        try {
            long batchSize = nonEmptyFiles.stream().mapToLong(MultipartFile::getSize).sum();
            if (!reserveUserStorage(userId, batchSize)) {
                return Result.fail(400, "个人文件空间不足，当前上限为1GB");
            }

            Path userUploadPath = uploadPath.resolve(String.valueOf(userId)).normalize();
            List<FileUploadVO> result = new ArrayList<>();
            List<Path> createdFiles = new ArrayList<>();
            try {
                Files.createDirectories(userUploadPath);
                for (MultipartFile uploadFile : nonEmptyFiles) {
                    String originalName = StringUtils.cleanPath(uploadFile.getOriginalFilename());
                    String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                    String storedName = UUID.randomUUID() + ext;
                    Path target = userUploadPath.resolve(storedName).normalize();
                    if (!target.startsWith(userUploadPath)) {
                        throw new IOException("非法文件名");
                    }
                    Files.copy(uploadFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                    createdFiles.add(target);

                    FileUploadVO vo = new FileUploadVO();
                    vo.setOriginalName(originalName);
                    vo.setFileUrl("/api/resource/files/" + userId + "/"
                            + UriUtils.encodePathSegment(storedName, StandardCharsets.UTF_8));
                    vo.setFileSize(uploadFile.getSize());
                    vo.setFileType(resolveFileType(uploadFile, originalName));
                    result.add(vo);
                }
            } catch (IOException error) {
                for (Path createdFile : createdFiles) {
                    Files.deleteIfExists(createdFile);
                }
                releaseUserStorage(userId, batchSize);
                throw error;
            }
            log.info("资源附件上传成功: userId={}, count={}, bytes={}", userId, result.size(), batchSize);
            return Result.ok(result);
        } finally {
            uploadSlots.release(permits);
        }
    }

    @Operation(summary = "访问资源附件")
    @GetMapping("/**")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(HttpServletRequest request) throws MalformedURLException {
        String prefix = "/api/resource/files/";
        String uri = request.getRequestURI();
        String filename = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
        filename = UriUtils.decode(filename, StandardCharsets.UTF_8);
        Path target = uploadPath.resolve(filename).normalize();
        if (!StringUtils.hasText(filename) || filename.contains("..") || !target.startsWith(uploadPath) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }

        UrlResource resource = new UrlResource(target.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.getFileName() + "\"")
                .body(resource);
    }

    private String resolveFileType(MultipartFile file, String originalName) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        int dotIndex = originalName.lastIndexOf('.');
        return dotIndex >= 0 ? originalName.substring(dotIndex + 1) : "application/octet-stream";
    }

    private String validateFile(MultipartFile file) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename());
        if (!StringUtils.hasText(originalName) || originalName.length() > 255
                || originalName.contains("..") || originalName.contains("/")
                || originalName.contains("\\")) {
            return "文件名不合法";
        }
        if (file.getSize() <= 0) {
            return originalName + "：空文件不能上传";
        }
        if (file.getSize() > maxFileSize) {
            return originalName + "：超过50MB";
        }
        int dotIndex = originalName.lastIndexOf('.');
        String extension = dotIndex >= 0
                ? originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!allowedExtensions.contains(extension)) {
            return originalName + "：不支持此文件类型";
        }
        return null;
    }

    private boolean reserveUserStorage(Long userId, long bytes) {
        AtomicLong usage = userStorageUsage.computeIfAbsent(userId,
                ignored -> new AtomicLong(calculateDirectorySize(uploadPath.resolve(String.valueOf(userId)))));
        long reserved = usage.addAndGet(bytes);
        if (reserved <= maxUserStorage) {
            return true;
        }
        usage.addAndGet(-bytes);
        return false;
    }

    private void releaseUserStorage(Long userId, long bytes) {
        AtomicLong usage = userStorageUsage.get(userId);
        if (usage != null) {
            usage.updateAndGet(current -> Math.max(0, current - bytes));
        }
    }

    private long calculateDirectorySize(Path directory) {
        if (!Files.exists(directory)) {
            return 0L;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ignored) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
