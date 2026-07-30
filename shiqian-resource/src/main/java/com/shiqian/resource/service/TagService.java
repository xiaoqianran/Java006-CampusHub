package com.shiqian.resource.service;

import com.shiqian.resource.entity.Tag;

import java.util.List;

public interface TagService {

    List<Tag> listTags(String keyword);

    Tag addTag(String name);

    Tag updateTag(Long id, String name);

    void deleteTag(Long id);
}
