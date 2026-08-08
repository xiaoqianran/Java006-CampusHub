package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.common.security.JwtUtil;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.matchesPattern;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceService resourceService;

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
                        matchesPattern("/api/resource/files/object/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data[0].mimeType").value("text/plain"))
                .andExpect(jsonPath("$.data[0].assetKind").value("CODE"));

        Integer objectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stored_object WHERE owner_id = 1 AND status = 'TEMPORARY'",
                Integer.class);
        Long usedBytes = jdbcTemplate.queryForObject(
                "SELECT used_bytes FROM t_user_storage_quota WHERE owner_id = 1",
                Long.class);
        org.junit.jupiter.api.Assertions.assertTrue(objectCount != null && objectCount >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(usedBytes != null && usedBytes >= 5L);
    }

    @Test
    public void testRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "unsafe.exe", "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("unsafe.exe：不支持此文件类型"));
    }

    @Test
    public void testRejectsPdfExtensionWithTextContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "fake.pdf", "application/pdf", "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("fake.pdf：文件内容与扩展名不匹配"));
    }

    @Test
    public void testRejectsDeclaredMimeMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "notes.txt", "image/png", "plain text".getBytes());

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("notes.txt：MIME 类型与扩展名不匹配"));
    }

    @Test
    public void testInvalidBatchDoesNotPersistPartialFilesOrQuota() throws Exception {
        Integer objectsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stored_object WHERE owner_id = 1",
                Integer.class);
        Long quotaBefore = jdbcTemplate.query(
                "SELECT used_bytes FROM t_user_storage_quota WHERE owner_id = 1",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L);

        mockMvc.perform(multipart("/api/resource/files")
                        .file(new MockMultipartFile(
                                "files", "valid.txt", "text/plain", "valid".getBytes()))
                        .file(new MockMultipartFile(
                                "files", "invalid.pdf", "application/pdf", "fake".getBytes()))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Integer objectsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stored_object WHERE owner_id = 1",
                Integer.class);
        Long quotaAfter = jdbcTemplate.query(
                "SELECT used_bytes FROM t_user_storage_quota WHERE owner_id = 1",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L);
        org.junit.jupiter.api.Assertions.assertEquals(objectsBefore, objectsAfter);
        org.junit.jupiter.api.Assertions.assertEquals(quotaBefore, quotaAfter);
    }

    @Test
    public void testRejectsUploadPathTraversal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "../notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件名不合法"));
    }

    @Test
    public void testTemporaryObjectIsPrivateAndPublishedBindingBecomesReadable() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "private.txt", "text/plain", "private content".getBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/resource/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray());
        String fileUrl = response.path("data").path(0).path("fileUrl").asText();

        mockMvc.perform(get(fileUrl))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(fileUrl)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("private content".getBytes()));
        mockMvc.perform(get(fileUrl + "/signed-url")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(
                        matchesPattern("/api/resource/files/object/[0-9a-f-]{36}\\?inline=false")));

        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName("private.txt");
        attachment.setFileUrl(fileUrl);
        attachment.setFileSize((long) "private content".getBytes().length);
        attachment.setFileType("txt");
        attachment.setMimeType("text/plain");
        ResourceCreateDTO create = new ResourceCreateDTO();
        create.setTitle("公开附件测试");
        create.setContentMarkdown("正文");
        create.setContentScene("SHARE");
        create.setAttachments(java.util.List.of(attachment));
        Resource resource = resourceService.createResource(1L, create);
        resourceService.auditResource(resource.getId(), 1, 2L);

        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes("private content".getBytes()));
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
                        .header("Authorization", "Bearer " + userToken)
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
                        .header("Authorization", "Bearer " + userToken)
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
                        .header("Authorization", "Bearer " + userToken)
                        .param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    public void testLegacyFileDeniedWithoutOwnershipOrPublished() throws Exception {
        Files.write(previewDirectory.resolve("secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/resource/files/1/secret.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testPreviewRejectsPathTraversal() throws Exception {
        mockMvc.perform(get("/api/resource/files/preview/text")
                        .param("path", "../application.yml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
