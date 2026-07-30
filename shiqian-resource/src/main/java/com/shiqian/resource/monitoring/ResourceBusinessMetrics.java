package com.shiqian.resource.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 资源域业务指标统一入口，避免业务代码散落指标名。
 */
@Component
public class ResourceBusinessMetrics {

    private final Counter publish;
    private final Counter audit;
    private final Counter auditReject;
    private final Counter search;
    private final Counter searchEmpty;
    private final Counter download;
    private final Counter uploadFailure;
    private final Counter rabbitConsumeFailure;
    private final Counter elasticsearchSyncFailure;

    public ResourceBusinessMetrics(MeterRegistry registry) {
        publish = counter(registry, "resource_publish", "成功发布资源数");
        audit = counter(registry, "resource_audit", "资源审核操作数");
        auditReject = counter(registry, "resource_audit_reject", "资源审核拒绝或退回数");
        search = counter(registry, "resource_search", "资源搜索请求数");
        searchEmpty = counter(registry, "resource_search_empty", "无结果资源搜索数");
        download = counter(registry, "resource_download", "已去重下载计数数");
        uploadFailure = counter(registry, "resource_upload_failure", "资源文件上传失败数");
        rabbitConsumeFailure = counter(registry, "rabbitmq_consume_failure", "RabbitMQ消费失败数");
        elasticsearchSyncFailure = counter(registry, "elasticsearch_sync_failure", "Elasticsearch同步失败数");
    }

    public void published() {
        publish.increment();
    }

    public void audited(boolean rejected) {
        audit.increment();
        if (rejected) auditReject.increment();
    }

    public void searched(boolean empty) {
        search.increment();
        if (empty) searchEmpty.increment();
    }

    public void downloaded() {
        download.increment();
    }

    public void uploadFailed() {
        uploadFailure.increment();
    }

    public void rabbitConsumeFailed() {
        rabbitConsumeFailure.increment();
    }

    public void elasticsearchSyncFailed() {
        elasticsearchSyncFailure.increment();
    }

    private Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(registry);
    }
}
