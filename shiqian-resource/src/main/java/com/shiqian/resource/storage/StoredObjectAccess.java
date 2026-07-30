package com.shiqian.resource.storage;

import com.shiqian.resource.entity.StoredObject;

import java.io.InputStream;

public record StoredObjectAccess(StoredObject metadata, InputStream inputStream) {
}
