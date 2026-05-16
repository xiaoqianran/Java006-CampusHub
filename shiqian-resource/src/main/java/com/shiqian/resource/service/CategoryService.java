package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Category;

import java.util.List;

public interface CategoryService {

    void addCategory(Category category);

    void updateCategory(Category category);

    void deleteCategory(Long id);

    Category getCategoryById(Long id);

    List<Category> getCategoryTree();

    Page<Category> pageCategories(Page<Category> page);
}
