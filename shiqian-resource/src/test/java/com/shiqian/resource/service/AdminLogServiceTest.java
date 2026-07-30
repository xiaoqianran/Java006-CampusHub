package com.shiqian.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.mapper.AdminLogMapper;
import com.shiqian.resource.service.impl.AdminLogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminLogServiceTest extends BaseResourceTest {

    @Autowired
    private AdminLogService adminLogService;
    @Autowired
    private AdminLogMapper adminLogMapper;

    @AfterEach
    void cleanUp() {
        adminLogMapper.delete(new QueryWrapper<>());
    }

    @Test
    void logsMustRemainQueryableFromANewServiceInstanceAndMaskSecrets() {
        adminLogService.recordLog(
                8L,
                "RESOURCE_APPROVE",
                99L,
                "{\"password\":\"plain-secret\",\"authorization\":\"Bearer aaa.bbb.ccc\"}");

        AdminLogService restartedService = new AdminLogServiceImpl(adminLogMapper);
        Page<AdminLog> page = restartedService.pageLogs(
                1, 20, "RESOURCE_APPROVE", 8L, null, null);

        assertEquals(1, page.getTotal());
        AdminLog saved = page.getRecords().get(0);
        assertEquals(99L, saved.getTargetId());
        assertFalse(saved.getDetail().contains("plain-secret"));
        assertFalse(saved.getDetail().contains("aaa.bbb.ccc"));
    }
}
