package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.tables.records.AccountsRecord;
import com.budgettracker.backend.model.Account;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;

@Repository
public class AccountRepository {

    private final DSLContext dsl;

    @Autowired
    public AccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Account> findById(Long id) {
        return dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToAccount);
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ACCOUNTS)
                        .where(ACCOUNTS.ID.eq(id))
        );
    }

    public List<Account> findByUserId(Long userId) {
        return dsl.selectFrom(ACCOUNTS)
                .where(ACCOUNTS.USER_ID.eq(userId))
                .fetch()
                .map(this::mapRecordToAccount);
    }

    @Transactional
    public Account save(Account account) {
        LocalDateTime now = LocalDateTime.now();
        if (account.getId() == null) {
            AccountsRecord record = dsl.insertInto(ACCOUNTS)
                    .set(ACCOUNTS.USER_ID, account.getUserId())
                    .set(ACCOUNTS.NAME, account.getName())
                    .set(ACCOUNTS.TYPE, account.getType())
                    .set(ACCOUNTS.BALANCE, account.getBalance() != null ? account.getBalance() : java.math.BigDecimal.ZERO)
                    .set(ACCOUNTS.CURRENCY, account.getCurrency())
                    .set(ACCOUNTS.CREATED_AT, now)
                    .set(ACCOUNTS.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToAccount(record);
        } else {
            dsl.update(ACCOUNTS)
                    .set(ACCOUNTS.NAME, account.getName())
                    .set(ACCOUNTS.TYPE, account.getType())
                    .set(ACCOUNTS.BALANCE, account.getBalance())
                    .set(ACCOUNTS.CURRENCY, account.getCurrency())
                    .set(ACCOUNTS.UPDATED_AT, now)
                    .where(ACCOUNTS.ID.eq(account.getId()))
                    .execute();
            account.setUpdatedAt(now);
            return account;
        }
    }

    /**
     * Atomically applies {@code delta} to an account's balance and returns the updated Account.
     * A positive delta increases the balance; a negative delta decreases it.
     */
    @Transactional
    public Account updateBalance(Long accountId, BigDecimal delta) {
        LocalDateTime now = LocalDateTime.now();
        dsl.update(ACCOUNTS)
                .set(ACCOUNTS.BALANCE, ACCOUNTS.BALANCE.add(delta))
                .set(ACCOUNTS.UPDATED_AT, now)
                .where(ACCOUNTS.ID.eq(accountId))
                .execute();
        return findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found after balance update: " + accountId));
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(id))
                .execute();
    }

    private Account mapRecordToAccount(AccountsRecord record) {
        if (record == null) {
            return null;
        }
        return Account.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .name(record.getName())
                .type(record.getType())
                .balance(record.getBalance())
                .currency(record.getCurrency())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
