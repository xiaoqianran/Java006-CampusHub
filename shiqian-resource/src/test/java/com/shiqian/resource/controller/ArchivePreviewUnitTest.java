package com.shiqian.resource.controller;

import com.shiqian.common.result.Result;
import com.shiqian.resource.config.ResourceStorageProperties;
import com.shiqian.resource.dto.ArchivePreviewVO;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.monitoring.ResourceBusinessMetrics;
import com.shiqian.resource.service.StoredObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ZIP 目录预览独立单元测试：不启 Spring 容器，直接测 previewArchive 逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ArchivePreviewUnitTest {

    @Mock
    private StoredObjectService storedObjectService;
    @Mock
    private ResourceStorageProperties storageProperties;
    @Mock
    private ResourceBusinessMetrics businessMetrics;
    @Mock
    private ResourceMapper resourceMapper;

    @TempDir
    Path tempDir;

    private ResourceFileController controller;
    private Path legacyRoot;

    @BeforeEach
    void setUp() throws IOException {
        controller = new ResourceFileController(
                storedObjectService,
                storageProperties,
                businessMetrics,
                resourceMapper);
        legacyRoot = tempDir.resolve("legacy");
        Files.createDirectories(legacyRoot);
        ReflectionTestUtils.setField(controller, "legacyUploadDir", legacyRoot.toString());
        // 小阈值便于测截断与扫描上限
        ReflectionTestUtils.setField(controller, "maxArchiveEntries", 3);
        ReflectionTestUtils.setField(controller, "maxArchiveScanEntries", 5);
        when(resourceMapper.isPublishedFileUrl(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (legacyRoot != null && Files.exists(legacyRoot)) {
            try (Stream<Path> walk = Files.walk(legacyRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    @Test
    void missingFileReturns404() throws Exception {
        Result<ArchivePreviewVO> result = controller.previewArchive("missing/nope.zip");
        assertEquals(404, result.getCode());
        assertEquals("预览文件不存在", result.getMessage());
    }

    @Test
    void nonZipExtensionReturns400() throws Exception {
        writeBytes("packs/notes.txt", "not a zip".getBytes(StandardCharsets.UTF_8));

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/notes.txt");
        assertEquals(400, result.getCode());
        assertEquals("当前仅支持预览 ZIP 压缩包目录", result.getMessage());
    }

    @Test
    void pathTraversalReturns404() throws Exception {
        Result<ArchivePreviewVO> result = controller.previewArchive("../secret.zip");
        assertEquals(404, result.getCode());
    }

    @Test
    void accessDeniedWhenNotPublishedReturns404() throws Exception {
        writeZip("packs/private.zip", 2);
        when(resourceMapper.isPublishedFileUrl(anyString())).thenReturn(false);

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/private.zip");
        assertEquals(404, result.getCode());
        assertEquals("预览文件不存在", result.getMessage());
    }

    @Test
    void listsZipEntriesWithoutTruncation() throws Exception {
        writeZip("packs/small.zip", 2);

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/small.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertEquals(2, data.getTotalEntries());
        assertEquals(2, data.getEntries().size());
        assertFalse(data.isTruncated());
        assertEquals("file-0.txt", data.getEntries().get(0).getName());
        assertFalse(data.getEntries().get(0).isDirectory());
    }

    @Test
    void truncatesListedEntriesWhenOverLimit() throws Exception {
        // limit=3, scanCap=5, 写 4 个 entry：列表截断但仍扫完
        writeZip("packs/over-limit.zip", 4);

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/over-limit.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertTrue(data.isTruncated());
        assertEquals(3, data.getEntries().size());
        assertEquals(4, data.getTotalEntries());
    }

    @Test
    void stopsAtScanCapToAvoidEntryBomb() throws Exception {
        // limit=3, scanCap=5, 写 20 个 entry：扫描在 5 处停止
        writeZip("packs/bomb.zip", 20);

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/bomb.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertTrue(data.isTruncated());
        assertEquals(3, data.getEntries().size());
        assertEquals(5, data.getTotalEntries());
    }

    @Test
    void longEntryNameIsShortened() throws Exception {
        String longName = "a".repeat(600) + ".txt";
        Path zipPath = legacyRoot.resolve("packs/long-name.zip");
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry(longName));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/long-name.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertEquals(1, data.getEntries().size());
        String name = data.getEntries().get(0).getName();
        assertEquals(500, name.length());
        assertTrue(name.endsWith("..."));
        assertTrue(name.startsWith("aaa"));
    }

    @Test
    void includesDirectoryEntries() throws Exception {
        Path zipPath = legacyRoot.resolve("packs/dirs.zip");
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("docs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("docs/readme.txt"));
            zip.write("hi".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/dirs.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertEquals(2, data.getTotalEntries());
        assertTrue(data.getEntries().get(0).isDirectory());
        assertEquals("docs/", data.getEntries().get(0).getName());
        assertEquals("docs/readme.txt", data.getEntries().get(1).getName());
    }

    @Test
    void garbageNamedZipIsTreatedAsEmptyArchive() throws Exception {
        // ZipInputStream 对无 LOCSIG 的垃圾数据 getNextEntry()=null，不抛异常。
        writeBytes("packs/garbage.zip", "this-is-not-a-valid-zip".getBytes(StandardCharsets.UTF_8));

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/garbage.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertEquals(0, data.getTotalEntries());
        assertTrue(data.getEntries().isEmpty());
        assertFalse(data.isTruncated());
    }

    @Test
    void truncatedZipReturns400() throws Exception {
        // 先写合法 ZIP，再截断到半截 DEFLATE 流，触发 EOFException → 400
        Path zipPath = legacyRoot.resolve("packs/truncated.zip");
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("payload.txt"));
            zip.write("hello-world-".repeat(200).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] full = Files.readAllBytes(zipPath);
        assertTrue(full.length > 40, "fixture zip too small");
        Files.write(zipPath, java.util.Arrays.copyOf(full, 40));

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/truncated.zip");
        assertEquals(400, result.getCode());
        assertEquals("ZIP 文件已损坏或格式不正确", result.getMessage());
    }

    @Test
    void emptyZipReturnsEmptyList() throws Exception {
        Path zipPath = legacyRoot.resolve("packs/empty.zip");
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // no entries
        }

        Result<ArchivePreviewVO> result = controller.previewArchive("packs/empty.zip");
        assertEquals(200, result.getCode());
        ArchivePreviewVO data = result.getData();
        assertNotNull(data);
        assertEquals(0, data.getTotalEntries());
        assertFalse(data.isTruncated());
    }

    private void writeZip(String relativePath, int entryCount) throws IOException {
        Path zipPath = legacyRoot.resolve(relativePath);
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (int i = 0; i < entryCount; i++) {
                zip.putNextEntry(new ZipEntry("file-" + i + ".txt"));
                zip.write(("content-" + i).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private void writeBytes(String relativePath, byte[] content) throws IOException {
        Path path = legacyRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(content);
        }
    }
}
