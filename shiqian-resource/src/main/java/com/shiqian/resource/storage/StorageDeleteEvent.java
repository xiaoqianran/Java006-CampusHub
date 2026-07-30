package com.shiqian.resource.storage;

public record StorageDeleteEvent(Long storedObjectId, String objectKey, String provider) {
}
