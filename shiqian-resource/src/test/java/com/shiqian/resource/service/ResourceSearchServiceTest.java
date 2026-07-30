package com.shiqian.resource.service;

import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.service.impl.ResourceSearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceSearchServiceTest {

    @Test
    void searchMustApplyPublishedTaxonomyChannelHighlightAndRequestedSort() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        @SuppressWarnings("unchecked")
        SearchHits<ResourceDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(operations.search(any(NativeQuery.class), eq(ResourceDocument.class)))
                .thenReturn(hits);
        ResourceSearchServiceImpl service = new ResourceSearchServiceImpl(operations);

        service.search(
                "中文课程",
                1,
                20,
                "downloads",
                "BLOG",
                8L,
                9L,
                "Java");

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ResourceDocument.class));
        NativeQuery query = captor.getValue();
        List<String> filterFields = query.getQuery().bool().filter().stream()
                .map(filter -> filter.term().field())
                .toList();

        assertEquals(List.of("status", "contentScene", "categoryIds", "tagIds", "tagNameKeys"),
                filterFields);
        assertTrue(query.getQuery().bool().must().get(0).multiMatch().fields()
                .contains("title^5"));
        assertNotNull(query.getHighlightQuery());
        assertEquals("downloadCount", query.getSortOptions().get(0).field().field());
        assertFalse(query.getSortOptions().isEmpty());
    }
}
