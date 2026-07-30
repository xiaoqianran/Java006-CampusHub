package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchivePreviewVO {

    private List<Entry> entries;
    private int totalEntries;
    private boolean truncated;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {

        private String name;
        private boolean directory;
        private long size;
        private long compressedSize;
    }
}
