package com.shiqian.resource.service;

public interface ResourceCounterService {

    boolean recordView(Long resourceId, Long userId, String clientIp);

    void recordDownload(Long resourceId);

    void flushPending();
}
