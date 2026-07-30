package com.shiqian.resource.service;

import com.shiqian.resource.dto.SensitiveWordDTO;
import com.shiqian.resource.entity.SensitiveWord;

import java.util.List;

public interface SensitiveWordService {
    List<SensitiveWord> list(String keyword);
    Long create(SensitiveWordDTO dto, Long operatorId);
    void update(Long id, SensitiveWordDTO dto);
    void delete(Long id);
    void reload();
}
