package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.tables.records.CategoriesRecord;
import com.budgettracker.backend.model.Category;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static org.jooq.impl.DSL.*;

@Repository
public class CategoryRepository {

    private final DSLContext dsl;

    @Autowired
    public CategoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Category> findById(Long id) {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToCategory);
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(CATEGORIES)
                        .where(CATEGORIES.ID.eq(id))
        );
    }

    public List<Category> findByUserIdAndSystemWide(Long userId) {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.USER_ID.eq(userId).or(CATEGORIES.USER_ID.isNull()))
                .fetch()
                .map(this::mapRecordToCategory);
    }

    @Transactional
    public Category save(Category category) {
        LocalDateTime now = LocalDateTime.now();
        if (category.getId() == null) {
            CategoriesRecord record = dsl.insertInto(CATEGORIES)
                    .set(CATEGORIES.USER_ID, category.getUserId())
                    .set(CATEGORIES.PARENT_ID, category.getParentId())
                    .set(CATEGORIES.NAME, category.getName())
                    .set(CATEGORIES.ICON, category.getIcon())
                    .set(CATEGORIES.COLOR, category.getColor())
                    .set(CATEGORIES.TYPE, category.getType())
                    .set(CATEGORIES.HIDDEN, category.isHidden())
                    .set(CATEGORIES.CREATED_AT, now)
                    .set(CATEGORIES.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToCategory(record);
        } else {
            dsl.update(CATEGORIES)
                    .set(CATEGORIES.PARENT_ID, category.getParentId())
                    .set(CATEGORIES.NAME, category.getName())
                    .set(CATEGORIES.ICON, category.getIcon())
                    .set(CATEGORIES.COLOR, category.getColor())
                    .set(CATEGORIES.TYPE, category.getType())
                    .set(CATEGORIES.HIDDEN, category.isHidden())
                    .set(CATEGORIES.UPDATED_AT, now)
                    .where(CATEGORIES.ID.eq(category.getId()))
                    .execute();
            category.setUpdatedAt(now);
            return category;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(CATEGORIES)
                .where(CATEGORIES.ID.eq(id))
                .execute();
    }

    public boolean hasTransactions(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(TRANSACTIONS)
                        .where(TRANSACTIONS.CATEGORY_ID.eq(id))
        );
    }

    public List<Long> getDescendantCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return new java.util.ArrayList<>();
        }

        CommonTableExpression<?> categoryTree = name("category_tree").fields("id")
                .as(select(CATEGORIES.ID)
                        .from(CATEGORIES)
                        .where(CATEGORIES.ID.eq(categoryId))
                        .unionAll(
                                select(CATEGORIES.ID)
                                        .from(CATEGORIES)
                                        .join(table(name("category_tree")))
                                        .on(CATEGORIES.PARENT_ID.eq(field(name("category_tree", "id"), Long.class)))
                        ));

        return dsl.withRecursive(categoryTree)
                .select(categoryTree.field("id", Long.class))
                .from(categoryTree)
                .fetchInto(Long.class);
    }

    public boolean hasDescendantTransactions(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        List<Long> descendantIds = getDescendantCategoryIds(categoryId);
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(TRANSACTIONS)
                        .where(TRANSACTIONS.CATEGORY_ID.in(descendantIds))
        );
    }

    public boolean hasCircularDependency(Long childId, Long parentId) {
        if (childId == null || parentId == null) {
            return false;
        }
        if (childId.equals(parentId)) {
            return true;
        }

        CommonTableExpression<?> ancestors = name("ancestors").fields("id", "parent_id")
                .as(select(CATEGORIES.ID, CATEGORIES.PARENT_ID)
                        .from(CATEGORIES)
                        .where(CATEGORIES.ID.eq(parentId))
                        .unionAll(
                                select(CATEGORIES.ID, CATEGORIES.PARENT_ID)
                                        .from(CATEGORIES)
                                        .join(table(name("ancestors")))
                                        .on(CATEGORIES.ID.eq(field(name("ancestors", "parent_id"), Long.class)))
                        ));

        List<Long> ancestorIds = dsl.withRecursive(ancestors)
                .select(ancestors.field("id", Long.class))
                .from(ancestors)
                .fetchInto(Long.class);

        return ancestorIds.contains(childId);
    }

    private Category mapRecordToCategory(CategoriesRecord record) {
        if (record == null) {
            return null;
        }
        return Category.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .parentId(record.getParentId())
                .name(record.getName())
                .icon(record.getIcon())
                .color(record.getColor())
                .type(record.getType())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .hidden(record.getHidden())
                .build();
    }
}
