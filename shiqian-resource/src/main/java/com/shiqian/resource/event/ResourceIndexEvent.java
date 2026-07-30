package com.shiqian.resource.event;

public record ResourceIndexEvent(Long resourceId, Operation operation) {

    public enum Operation {
        UPSERT,
        DELETE
    }

    public static ResourceIndexEvent upsert(Long resourceId) {
        return new ResourceIndexEvent(resourceId, Operation.UPSERT);
    }

    public static ResourceIndexEvent delete(Long resourceId) {
        return new ResourceIndexEvent(resourceId, Operation.DELETE);
    }
}
