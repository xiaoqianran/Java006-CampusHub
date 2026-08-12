package com.shiqian.resource.service;

/**
 * Review-side resource operations: audit / status transitions.
 */
public interface ResourceReviewService {

    void auditResource(Long resourceId, Integer status, Long operatorId);

    void reviewResource(Long resourceId, Integer status, String reason, Long operatorId);
}
