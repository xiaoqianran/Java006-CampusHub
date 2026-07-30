package com.shiqian.resource.storage;

import com.shiqian.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FileValidationService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "java", "py", "js", "ts", "vue", "c", "cpp", "h",
            "go", "rs", "sql", "json", "xml", "yaml", "yml", "html", "css", "sh");
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "rar", "7z");
    private static final Set<String> ZIP_CONTAINER_EXTENSIONS =
            Set.of("zip", "docx", "xlsx", "pptx");
    private static final Set<String> OLE_EXTENSIONS = Set.of("doc", "xls", "ppt");

    private final long maxFileSize;
    private final Set<String> allowedExtensions;

    public FileValidationService(
            @Value("${resource.upload.max-file-size:52428800}") long maxFileSize,
            @Value("${resource.upload.allowed-extensions}") String allowedExtensions) {
        this.maxFileSize = maxFileSize;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    public ValidatedFile validate(MultipartFile file) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename());
        if (!StringUtils.hasText(originalName) || originalName.length() > 255
                || originalName.contains("..") || originalName.contains("/")
                || originalName.contains("\\")) {
            throw new BusinessException("文件名不合法");
        }
        if (file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(originalName + "：空文件不能上传");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(originalName + "：超过" + formatLimit(maxFileSize));
        }

        int dotIndex = originalName.lastIndexOf('.');
        String extension = dotIndex >= 0
                ? originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(originalName + "：不支持此文件类型");
        }

        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(8192);
        } catch (IOException error) {
            throw new BusinessException(originalName + "：文件读取失败");
        }
        String detectedMime = validateMagicNumber(originalName, extension, header);
        validateDeclaredMime(originalName, extension, file.getContentType());
        return new ValidatedFile(
                originalName,
                extension,
                detectedMime,
                assetKind(extension),
                file.getSize());
    }

    private String validateMagicNumber(String originalName, String extension, byte[] header) {
        boolean valid;
        String mime;
        switch (extension) {
            case "pdf" -> {
                valid = startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
                mime = "application/pdf";
            }
            case "jpg", "jpeg" -> {
                valid = startsWith(header, 0xFF, 0xD8, 0xFF);
                mime = "image/jpeg";
            }
            case "png" -> {
                valid = startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                mime = "image/png";
            }
            case "gif" -> {
                valid = startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                        || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
                mime = "image/gif";
            }
            case "webp" -> {
                valid = header.length >= 12
                        && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                        && matchesAt(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
                mime = "image/webp";
            }
            case "rar" -> {
                valid = startsWith(header, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07);
                mime = "application/vnd.rar";
            }
            case "7z" -> {
                valid = startsWith(header, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C);
                mime = "application/x-7z-compressed";
            }
            default -> {
                if (ZIP_CONTAINER_EXTENSIONS.contains(extension)) {
                    valid = startsWith(header, 0x50, 0x4B, 0x03, 0x04)
                            || startsWith(header, 0x50, 0x4B, 0x05, 0x06)
                            || startsWith(header, 0x50, 0x4B, 0x07, 0x08);
                    mime = switch (extension) {
                        case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                        case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                        case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                        default -> "application/zip";
                    };
                } else if (OLE_EXTENSIONS.contains(extension)) {
                    valid = startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
                    mime = "application/x-ole-storage";
                } else if (TEXT_EXTENSIONS.contains(extension)) {
                    valid = isUtf8Text(header);
                    mime = textMime(extension);
                } else {
                    valid = false;
                    mime = "application/octet-stream";
                }
            }
        }
        if (!valid) {
            throw new BusinessException(originalName + "：文件内容与扩展名不匹配");
        }
        return mime;
    }

    private void validateDeclaredMime(String originalName, String extension, String contentType) {
        if (!StringUtils.hasText(contentType)
                || MediaTypes.isGeneric(contentType)) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        boolean valid;
        if (IMAGE_EXTENSIONS.contains(extension)) {
            valid = normalized.startsWith("image/");
        } else if ("pdf".equals(extension)) {
            valid = "application/pdf".equals(normalized);
        } else if (TEXT_EXTENSIONS.contains(extension)) {
            valid = normalized.startsWith("text/")
                    || normalized.startsWith("application/x-")
                    || normalized.contains("json")
                    || normalized.contains("xml")
                    || normalized.contains("javascript")
                    || normalized.contains("yaml")
                    || normalized.contains("sql");
        } else {
            valid = normalized.startsWith("application/")
                    || "application/x-zip-compressed".equals(normalized);
        }
        if (!valid) {
            throw new BusinessException(originalName + "：MIME 类型与扩展名不匹配");
        }
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private String textMime(String extension) {
        return switch (extension) {
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "text/javascript";
            case "md" -> "text/markdown";
            default -> "text/plain";
        };
    }

    private String assetKind(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) return "IMAGE";
        if (ARCHIVE_EXTENSIONS.contains(extension)) return "ARCHIVE";
        if (TEXT_EXTENSIONS.contains(extension)) return "CODE";
        return "DOCUMENT";
    }

    private String formatLimit(long bytes) {
        if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024) + "MB";
        if (bytes >= 1024) return (bytes / 1024) + "KB";
        return bytes + "B";
    }

    private boolean startsWith(byte[] actual, byte[] expected) {
        return matchesAt(actual, 0, expected);
    }

    private boolean startsWith(byte[] actual, int... expected) {
        if (actual.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((actual[index] & 0xFF) != expected[index]) return false;
        }
        return true;
    }

    private boolean matchesAt(byte[] actual, int offset, byte[] expected) {
        if (actual.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (actual[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private static final class MediaTypes {
        private static boolean isGeneric(String contentType) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            return "application/octet-stream".equals(normalized)
                    || "binary/octet-stream".equals(normalized);
        }
    }
}
