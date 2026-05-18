package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "资源文件", description = "资源附件上传与访问")
@Slf4j
@RestController
@RequestMapping("/api/resource/files")
public class ResourceFileController {

    private final Path uploadPath;

    public ResourceFileController(@Value("${resource.upload-dir:uploads/resources}") String uploadDir) {
        this.uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Operation(summary = "批量上传资源附件")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:create')")
    public Result<List<FileUploadVO>> uploadFiles(@RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return Result.fail(400, "请选择要上传的文件");
        }

        Files.createDirectories(uploadPath);
        List<FileUploadVO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null
                    ? "resource-file"
                    : file.getOriginalFilename());
            String ext = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                ext = originalName.substring(dotIndex);
            }
            String storedName = UUID.randomUUID() + ext;
            Path target = uploadPath.resolve(storedName).normalize();
            if (!target.startsWith(uploadPath)) {
                return Result.fail(400, "非法文件名");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            FileUploadVO vo = new FileUploadVO();
            vo.setOriginalName(originalName);
            vo.setFileUrl("/api/resource/files/" + UriUtils.encodePathSegment(storedName, StandardCharsets.UTF_8));
            vo.setFileSize(file.getSize());
            vo.setFileType(resolveFileType(file, originalName));
            result.add(vo);
        }
        log.info("资源附件上传成功: count={}", result.size());
        return Result.ok(result);
    }

    @Operation(summary = "访问资源附件")
    @GetMapping("/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(@PathVariable String filename) throws MalformedURLException {
        Path target = uploadPath.resolve(filename).normalize();
        if (!target.startsWith(uploadPath) || !Files.exists(target)) {
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
}
