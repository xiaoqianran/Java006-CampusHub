package com.shiqian.user.controller;

import com.shiqian.common.user.InternalApiHeaders;
import com.shiqian.user.entity.User;
import com.shiqian.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalUserControllerTest {

    private static final String SERVICE_KEY = "test-only-internal-service-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM t_user");
        jdbcTemplate.execute("ALTER TABLE t_user ALTER COLUMN id RESTART WITH 1");
    }

    @Test
    void shouldReturnOnlyPublicFieldsInRequestOrderAndDeduplicateIds() throws Exception {
        User alice = user("alice", "小艾", "alice@example.com", "13800138000");
        userMapper.insert(alice);

        String response = mockMvc.perform(post("/internal/users/public-profiles/batch")
                        .header(InternalApiHeaders.SERVICE_KEY, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":[%d,%d,99999]}
                                """.formatted(alice.getId(), alice.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(alice.getId()))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].nickname").value("小艾"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("encoded-password"));
        assertFalse(response.contains("alice@example.com"));
        assertFalse(response.contains("13800138000"));
        assertFalse(response.contains("\"role\""));
    }

    @Test
    void shouldRejectMissingOrIncorrectServiceKey() throws Exception {
        String body = "{\"userIds\":[1]}";

        mockMvc.perform(post("/internal/users/public-profiles/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/internal/users/public-profiles/batch")
                        .header(InternalApiHeaders.SERVICE_KEY, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldRejectEmptyBatch() throws Exception {
        mockMvc.perform(post("/internal/users/public-profiles/batch")
                        .header(InternalApiHeaders.SERVICE_KEY, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private User user(String username, String nickname, String email, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPassword("encoded-password");
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatar("https://example.com/avatar.png");
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setTokenVersion(0L);
        user.setDeleted(0);
        return user;
    }
}
