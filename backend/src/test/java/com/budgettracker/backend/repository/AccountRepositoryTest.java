package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("account-repo-user@example.com")
                .googleSub("account-repo-sub")
                .build());
    }

    @Test
    public void testSave_NewAccount_Defaults() {
        Account account = Account.builder()
                .userId(testUser.getId())
                .name("Checking Null Balance")
                .type(AccountType.CHECKING)
                .balance(null) // Should default to BigDecimal.ZERO
                .currency("EUR")
                .build();

        Account saved = accountRepository.save(account);
        assertNotNull(saved.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getBalance()));
    }

    @Test
    public void testSave_UpdateAccount() {
        Account account = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Savings")
                .type(AccountType.SAVINGS)
                .balance(new BigDecimal("100.00"))
                .currency("EUR")
                .build());

        Long id = account.getId();

        // Update fields
        account.setName("Updated Savings");
        account.setBalance(new BigDecimal("200.00"));

        Account updated = accountRepository.save(account);
        assertEquals(id, updated.getId());
        assertEquals("Updated Savings", updated.getName());
        assertEquals(new BigDecimal("200.00"), updated.getBalance());
    }

    @Test
    public void testUpdateBalance_NonExistentAccount() {
        assertThrows(RuntimeException.class, () -> {
            accountRepository.updateBalance(9999L, BigDecimal.TEN);
        });
    }

    @Test
    public void testFindByUserId() {
        accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Acc 1")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .build());

        List<Account> accounts = accountRepository.findByUserId(testUser.getId());
        assertEquals(1, accounts.size());
        assertEquals("Acc 1", accounts.get(0).getName());
    }
}
