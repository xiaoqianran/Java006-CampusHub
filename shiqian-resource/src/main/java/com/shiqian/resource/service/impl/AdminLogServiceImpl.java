package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.service.AdminLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存实现的轻量审计日志服务，无需数据库、无需MyBatis
 */
@Service
public class AdminLogServiceImpl implements AdminLogService {

    private final List<AdminLog> logs = new CopyOnWriteArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public void recordLog(Long operatorId, String action, Long targetId, String detail) {
        if (action == null || action.isBlank()) {
            return;
        }
        AdminLog log = new AdminLog();
        log.setId(idGenerator.getAndIncrement());
        log.setOperatorId(operatorId != null ? operatorId : 0L);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        logs.add(log);
        // 简单限制内存大小，保留最近1000条
        if (logs.size() > 1000) {
            logs.remove(0);
        }
    }

    @Override
    public Page<AdminLog> pageLogs(Integer page, Integer size, String action) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, 100);

        List<AdminLog> filtered = new ArrayList<>();
        for (int i = logs.size() - 1; i >= 0; i--) { // 倒序，最新的在前
            AdminLog l = logs.get(i);
            if (action == null || action.isBlank() || action.equals(l.getAction())) {
                filtered.add(l);
            }
        }

        long total = filtered.size();
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        List<AdminLog> pageRecords = filtered.subList(from, to);

        Page<AdminLog> result = new Page<>(p, s);
        result.setRecords(pageRecords);
        result.setTotal(total);
        result.setCurrent(p);
        result.setSize(s);
        long pages = total == 0 ? 0 : (total + s - 1) / s;
        result.setPages(pages);
        return result;
    }
}
