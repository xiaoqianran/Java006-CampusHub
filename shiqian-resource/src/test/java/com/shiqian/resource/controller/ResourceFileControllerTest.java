package com.shiqian.resource.controller;

import com.shiqian.common.security.JwtUtil;
import com.shiqian.resource.BaseResourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class ResourceFileControllerTest extends BaseResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;

    @BeforeEach
    public void setUp() {
        userToken = jwtUtil.generateAccessToken(1L, "testuser", "USER");
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
                        org.hamcrest.Matchers.startsWith("/api/resource/files/1/")));
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
}
