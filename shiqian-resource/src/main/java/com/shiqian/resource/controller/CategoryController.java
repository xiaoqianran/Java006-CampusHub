package com.shiqian.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.result.Result;
import com.shiqian.resource.dto.CategoryDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Tag(name = "分类管理", description = "资源分类的增删改查接口")
@Slf4j
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "新增分类")
    @PostMapping
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> addCategory(@RequestBody @Valid CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        categoryService.addCategory(category);
        return Result.ok();
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> updateCategory(@PathVariable Long id,
                                       @RequestBody @Valid CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setId(id);
        categoryService.updateCategory(category);
        return Result.ok();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resource:audit')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }

    @Operation(summary = "根据ID获取分类")
    @GetMapping("/{id}")
    public Result<Category> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id);
        return Result.ok(category);
    }

    @Operation(summary = "获取分类树")
    @GetMapping("/tree")
    public Result<List<Category>> getCategoryTree() {
        List<Category> tree = categoryService.getCategoryTree();
        return Result.ok(tree);
    }

    @Operation(summary = "分页查询分类列表")
    @GetMapping
    public Result<Page<Category>> pageCategories(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Category> pageParam = new Page<>(page, size);
        Page<Category> result = categoryService.pageCategories(pageParam);
        return Result.ok(result);
    }
}
