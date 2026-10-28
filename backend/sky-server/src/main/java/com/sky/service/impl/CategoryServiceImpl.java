package com.sky.service.impl;

import org.springframework.context.annotation.Profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.CategorySaveDTO;
import com.sky.dto.CategoryStatusUpdateDTO;
import com.sky.entity.Category;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.CategoryMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.service.EmployeePermissionService;
import com.sky.vo.CategoryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!bootstrap")
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final EmployeePermissionService permissionService;

    public CategoryServiceImpl(
            CategoryMapper categoryMapper,
            EmployeePermissionService permissionService) {
        this.categoryMapper = categoryMapper;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public CategoryVO create(CategorySaveDTO dto) {
        permissionService.requireOperationsAccess();

        if (!Long.valueOf(0).equals(dto.getId())) {
            throw new BadRequestException("新增分类时id只能为0");
        }

        ensureNameAvailable(dto.getName(), null);

        Category category = new Category();
        category.setName(dto.getName());
        category.setType(dto.getType());
        category.setSort(dto.getSort());
        category.setStatus(dto.getStatus());

        try {
            int rows = categoryMapper.insert(category);

            if (rows != 1) {
                throw new IllegalStateException("新增分类失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("分类名称已存在");
        }

        return toVO(category);
    }

    @Override
    @Transactional
    public CategoryVO update(CategorySaveDTO dto) {
        permissionService.requireOperationsAccess();

        Category target = findForUpdate(dto.getId());

        ensureNameAvailable(dto.getName(), target.getId());

        boolean typeChanged =
                !Objects.equals(dto.getType(), target.getType());

        if (typeChanged && categoryMapper.hasReferences(target.getId())) {
            throw new ConflictException(
                    "分类已关联菜品或套餐，不能修改类型"
            );
        }

        boolean unchanged =
                Objects.equals(dto.getName(), target.getName())
                        && Objects.equals(dto.getType(), target.getType())
                        && Objects.equals(dto.getSort(), target.getSort())
                        && Objects.equals(dto.getStatus(), target.getStatus());

        if (unchanged) {
            return toVO(target);
        }

        // 使用空的新实体接收审计值，业务更新字段仍由Wrapper明确指定。
        Category auditEntity = new Category();
        try {
            int rows = categoryMapper.update(
                    auditEntity,
                    new LambdaUpdateWrapper<Category>()
                            .eq(Category::getId, target.getId())
                            .set(Category::getName, dto.getName())
                            .set(Category::getType, dto.getType())
                            .set(Category::getSort, dto.getSort())
                            .set(Category::getStatus, dto.getStatus())
            );

            if (rows != 1) {
                throw new IllegalStateException("编辑分类失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("分类名称已存在");
        }

        target.setName(dto.getName());
        target.setType(dto.getType());
        target.setSort(dto.getSort());
        target.setStatus(dto.getStatus());

        return toVO(target);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        permissionService.requireOperationsAccess();

        Category target = findForUpdate(id);

        if (categoryMapper.hasReferences(target.getId())) {
            throw new ConflictException(
                    "分类已关联菜品或套餐，不能删除"
            );
        }

        int rows = categoryMapper.deleteById(target.getId());

        if (rows != 1) {
            throw new IllegalStateException("删除分类失败");
        }
    }

    @Override
    @Transactional
    public CategoryVO updateStatus(
            Long id,
            CategoryStatusUpdateDTO dto) {

        permissionService.requireOperationsAccess();

        Category target = findForUpdate(id);

        if (Objects.equals(dto.getStatus(), target.getStatus())) {
            return toVO(target);
        }

        Category auditEntity = new Category();
        int rows = categoryMapper.update(
                auditEntity,
                new LambdaUpdateWrapper<Category>()
                        .eq(Category::getId, target.getId())
                        .set(Category::getStatus, dto.getStatus())
        );

        if (rows != 1) {
            throw new IllegalStateException("修改分类状态失败");
        }

        target.setStatus(dto.getStatus());

        return toVO(target);
    }

    @Override
    public PageResult<CategoryVO> pageQuery(
            CategoryPageQueryDTO dto) {

        permissionService.requireOperationsAccess();

        LambdaQueryWrapper<Category> query =
                new LambdaQueryWrapper<Category>()
                        .eq(
                                dto.getType() != null,
                                Category::getType,
                                dto.getType()
                        )
                        .eq(
                                dto.getStatus() != null,
                                Category::getStatus,
                                dto.getStatus()
                        );

        String name = dto.getName();

        if (name != null && !name.isBlank()) {
            String pattern = "%" + escapeLike(name) + "%";

            query.apply(
                    "name LIKE {0} ESCAPE '!'",
                    pattern
            );
        }

        query.orderByAsc(Category::getSort, Category::getId);

        Page<Category> page =
                new Page<>(dto.getPage(), dto.getPageSize());

        categoryMapper.selectPage(page, query);

        List<CategoryVO> records = page.getRecords()
                .stream()
                .map(this::toVO)
                .toList();

        return new PageResult<>(page.getTotal(), records);
    }

    @Override
    public List<CategoryVO> listForAdmin(Integer type) {
        permissionService.requireOperationsAccess();

        return queryList(type, null);
    }

    @Override
    public List<CategoryVO> listForUser(Integer type) {
        // 用户身份由后文的用户端JWT拦截器检查。
        // 后端强制只查启用分类，客户端不能覆盖。
        return queryList(type, 1);
    }

    private List<CategoryVO> queryList(
            Integer type,
            Integer status) {

        if (type != null && type != 1 && type != 2) {
            throw new BadRequestException("分类类型只能为1或2");
        }

        return categoryMapper.selectList(
                        new LambdaQueryWrapper<Category>()
                                .eq(
                                        type != null,
                                        Category::getType,
                                        type
                                )
                                .eq(
                                        status != null,
                                        Category::getStatus,
                                        status
                                )
                                .orderByAsc(
                                        Category::getSort,
                                        Category::getId
                                )
                )
                .stream()
                .map(this::toVO)
                .toList();
    }

    private Category findForUpdate(Long id) {
        if (id == null || id <= 0 || id > 9007199254740991L) {
            throw new BadRequestException("分类ID不合法");
        }

        Category target = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getId, id)
                        .last("FOR UPDATE")
        );

        if (target == null) {
            throw new NotFoundException("分类不存在");
        }

        return target;
    }

    private void ensureNameAvailable(
            String name,
            Long excludedId) {

        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, name)
                        .ne(
                                excludedId != null,
                                Category::getId,
                                excludedId
                        )
        );

        if (count != null && count > 0) {
            throw new ConflictException("分类名称已存在");
        }
    }

    private CategoryVO toVO(Category category) {
        return new CategoryVO(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getSort(),
                category.getStatus()
        );
    }

    private String escapeLike(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
