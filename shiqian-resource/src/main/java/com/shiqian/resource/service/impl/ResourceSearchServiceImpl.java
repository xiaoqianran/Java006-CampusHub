package com.shiqian.resource.service.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.service.ResourceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceSearchServiceImpl implements ResourceSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ResourceDocument> search(String keyword, Integer page, Integer size, String sort) {
        return search(keyword, page, size, sort, null);
    }

    @Override
    public Page<ResourceDocument> search(
            String keyword,
            Integer page,
            Integer size,
            String sort,
            String contentScene) {
        return search(keyword, page, size, sort, contentScene, null, null, null);
    }

    @Override
    public Page<ResourceDocument> search(
            String keyword,
            Integer page,
            Integer size,
            String sort,
            String contentScene,
            Long categoryId,
            Long tagId,
            String tagName) {
        String text = StringUtils.hasText(keyword) ? keyword.trim() : "";
        String scene = StringUtils.hasText(contentScene)
                ? contentScene.trim().toUpperCase(Locale.ROOT)
                : null;
        String normalizedTag = StringUtils.hasText(tagName)
                ? tagName.trim().toLowerCase(Locale.ROOT)
                : null;

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t
                                    .field("status")
                                    .value(1)));
                            if (StringUtils.hasText(text)) {
                                b.must(must -> must.multiMatch(m -> m
                                        .query(text)
                                        .fields(
                                                "title^5",
                                                "summary^3",
                                                "description^2",
                                                "markdownContent",
                                                "tagNames^3",
                                                "categoryNames^2")
                                ));
                                b.should(s -> s.matchPhrase(m -> m
                                                .field("title")
                                                .query(text)
                                                .boost(6.0f)))
                                        .should(s -> s.matchPhrase(m -> m
                                                .field("summary")
                                                .query(text)
                                                .boost(3.0f)));
                            }
                            if (scene != null) {
                                b.filter(f -> f.term(t -> t
                                        .field("contentScene")
                                        .value(scene)));
                            }
                            if (categoryId != null) {
                                b.filter(f -> f.term(t -> t
                                        .field("categoryIds")
                                        .value(categoryId)));
                            }
                            if (tagId != null) {
                                b.filter(f -> f.term(t -> t
                                        .field("tagIds")
                                        .value(tagId)));
                            }
                            if (normalizedTag != null) {
                                b.filter(f -> f.term(t -> t
                                        .field("tagNameKeys")
                                        .value(normalizedTag)));
                            }
                            return b;
                        })
                )
                .withHighlightQuery(new HighlightQuery(
                        new Highlight(List.of(
                                new HighlightField("title"),
                                new HighlightField("summary"),
                                new HighlightField("description"),
                                new HighlightField("markdownContent"),
                                new HighlightField("tagNames"))),
                        ResourceDocument.class))
                .withPageable(PageRequest.of(page - 1, size));

        applySort(queryBuilder, sort, StringUtils.hasText(text));
        NativeQuery query = queryBuilder.build();

        SearchHits<ResourceDocument> searchHits =
                elasticsearchOperations.search(query, ResourceDocument.class);
        List<ResourceDocument> documents = searchHits.getSearchHits().stream()
                .map(hit -> {
                    ResourceDocument document = hit.getContent();
                    document.setHighlights(hit.getHighlightFields());
                    return document;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(
                documents,
                PageRequest.of(page - 1, size),
                searchHits.getTotalHits());
    }

    private void applySort(
            NativeQueryBuilder queryBuilder,
            String requestedSort,
            boolean hasKeyword) {
        String sort = StringUtils.hasText(requestedSort)
                ? requestedSort.trim().toLowerCase(Locale.ROOT)
                : (hasKeyword ? "relevance" : "latest");
        List<SortOptions> sorts = new ArrayList<>();
        switch (sort) {
            case "oldest" -> sorts.add(fieldSort("createTime", SortOrder.Asc));
            case "hottest" -> {
                sorts.add(fieldSort("downloadCount", SortOrder.Desc));
                sorts.add(fieldSort("viewCount", SortOrder.Desc));
                sorts.add(fieldSort("createTime", SortOrder.Desc));
            }
            case "downloads" -> {
                sorts.add(fieldSort("downloadCount", SortOrder.Desc));
                sorts.add(fieldSort("createTime", SortOrder.Desc));
            }
            case "views" -> {
                sorts.add(fieldSort("viewCount", SortOrder.Desc));
                sorts.add(fieldSort("createTime", SortOrder.Desc));
            }
            case "latest" -> sorts.add(fieldSort("createTime", SortOrder.Desc));
            case "relevance" -> {
                // Elasticsearch 默认按 _score 排序。
            }
            default -> sorts.add(fieldSort("createTime", SortOrder.Desc));
        }
        if (!sorts.isEmpty()) {
            queryBuilder.withSort(sorts);
        }
    }

    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(sort -> sort.field(value -> value
                .field(field)
                .order(order)
                .missing("_last")));
    }
}
