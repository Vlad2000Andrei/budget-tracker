package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.tables.records.UsersRecord;
import com.budgettracker.backend.model.User;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    @Autowired
    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<User> findById(Long id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToUser);
    }

    public Optional<User> findByGoogleSub(String googleSub) {
        return dsl.selectFrom(USERS)
                .where(USERS.GOOGLE_SUB.eq(googleSub))
                .fetchOptional()
                .map(this::mapRecordToUser);
    }

    @Transactional
    public User save(User user) {
        LocalDateTime now = LocalDateTime.now();
        if (user.getId() == null) {
            UsersRecord record = dsl.insertInto(USERS)
                    .set(USERS.EMAIL, user.getEmail())
                    .set(USERS.GOOGLE_SUB, user.getGoogleSub())
                    .set(USERS.DEFAULT_CURRENCY, user.getDefaultCurrency() != null ? user.getDefaultCurrency() : "USD")
                    .set(USERS.CREATED_AT, now)
                    .set(USERS.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToUser(record);
        } else {
            dsl.update(USERS)
                    .set(USERS.EMAIL, user.getEmail())
                    .set(USERS.GOOGLE_SUB, user.getGoogleSub())
                    .set(USERS.DEFAULT_CURRENCY, user.getDefaultCurrency())
                    .set(USERS.UPDATED_AT, now)
                    .where(USERS.ID.eq(user.getId()))
                    .execute();
            user.setUpdatedAt(now);
            return user;
        }
    }

    private User mapRecordToUser(UsersRecord record) {
        if (record == null) {
            return null;
        }
        return User.builder()
                .id(record.getId())
                .email(record.getEmail())
                .googleSub(record.getGoogleSub())
                .defaultCurrency(record.getDefaultCurrency())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
