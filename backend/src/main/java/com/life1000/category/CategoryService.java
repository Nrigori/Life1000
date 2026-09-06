package com.life1000.category;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.Category;
import com.life1000.mapper.CategoryMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class CategoryService {
    private final CategoryMapper mapper;

    public CategoryService(CategoryMapper mapper) { this.mapper = mapper; }

    public List<Category> list() {
        return mapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder, Category::getId));
    }

    public Category get(long id) {
        Category category = mapper.selectById(id);
        if (category == null) throw ApiException.notFound("分类不存在");
        return category;
    }

    @Transactional
    public Category create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name().strip());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        mapper.insert(category);
        return get(category.getId());
    }

    @Transactional
    public Category update(long id, CategoryRequest request) {
        get(id);
        mapper.update(null, new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, id)
                .set(Category::getName, request.name().strip())
                .set(request.sortOrder() != null, Category::getSortOrder, request.sortOrder()));
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        // The FK sets related goals' category_id to NULL; goals are never deleted here.
        if (mapper.deleteById(id) == 0) throw ApiException.notFound("分类不存在");
    }
}
