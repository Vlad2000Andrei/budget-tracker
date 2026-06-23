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
        List<Long> descendantIds = new java.util.ArrayList<>();
        if (categoryId == null) {
            return descendantIds;
        }
        descendantIds.add(categoryId);

        // Accumulate descendants level-by-level (BFS traversal)
        List<Long> currentLevelIds = List.of(categoryId);
        while (!currentLevelIds.isEmpty()) {
            List<Long> nextLevelIds = dsl.select(CATEGORIES.ID)
                    .from(CATEGORIES)
                    .where(CATEGORIES.PARENT_ID.in(currentLevelIds))
                    .fetchInto(Long.class);
            if (!nextLevelIds.isEmpty()) {
                descendantIds.addAll(nextLevelIds);
            }
            currentLevelIds = nextLevelIds;
        }
        return descendantIds;
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
        Long currentParentId = parentId;
        int depth = 0;
        // Safety check: prevent infinite loops if data is already corrupt (depth limit 100)
        while (currentParentId != null && depth < 100) {
            if (currentParentId.equals(childId)) {
                return true;
            }
            currentParentId = dsl.select(CATEGORIES.PARENT_ID)
                    .from(CATEGORIES)
                    .where(CATEGORIES.ID.eq(currentParentId))
                    .fetchOneInto(Long.class);
            depth++;
        }
        return false;
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
