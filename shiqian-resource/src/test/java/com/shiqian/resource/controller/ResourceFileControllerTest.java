package com.shiqian.resource.controller;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.resource.BaseResourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class ResourceFileControllerTest extends BaseResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private final Path previewDirectory = Path.of("target/test-uploads/1");

    @BeforeEach
    public void setUp() throws IOException {
        userToken = jwtUtil.generateAccessToken(1L, "testuser", "USER");
        Files.createDirectories(previewDirectory);
    }

    @Test
    public void testUploadTxtSuccessAndUsesUserDirectory() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].originalName").value("notes.txt"))
                        .andExpect(jsonPath("$.data[0].fileUrl").value(
                        startsWith("/api/resource/files/1/")));
    }

    @Test
    public void testRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "unsafe.exe", "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("unsafe.exe：不支持此文件类型"));
    }

    @Test
    public void testRejectsMoreThanTenFiles() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/resource/files");
        for (int index = 0; index < 11; index++) {
            request.file(new MockMultipartFile(
                    "files", "file-" + index + ".txt", "text/plain", new byte[]{1}));
        }

        mockMvc.perform(request.header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("单次最多上传10个文件"));
    }

    @Test
    public void testPreviewMarkdownAsLimitedText() throws Exception {
        Files.writeString(
                previewDirectory.resolve("preview.md"),
                "# 标题\n\n正文内容",
                StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/resource/files/preview/text")
                        .param("path", "1/preview.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").value("# 标题\n\n正文内容"))
                .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    public void testPreviewZipDirectoryWithoutExtracting() throws Exception {
        Path zipPath = previewDirectory.resolve("preview.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            output.putNextEntry(new ZipEntry("docs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("docs/readme.txt"));
            output.write("hello".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        mockMvc.perform(get("/api/resource/files/preview/archive")
                        .param("path", "1/preview.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalEntries").value(2))
                .andExpect(jsonPath("$.data.entries[0].directory").value(true))
                .andExpect(jsonPath("$.data.entries[1].name").value("docs/readme.txt"));
    }

    @Test
    public void testInlinePdfUsesPreviewHeaders() throws Exception {
        Files.write(previewDirectory.resolve("preview.pdf"), "%PDF-test".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/resource/files/1/preview.pdf")
                        .param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    public void testPreviewRejectsPathTraversal() throws Exception {
        mockMvc.perform(get("/api/resource/files/preview/text")
                        .param("path", "../application.yml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
