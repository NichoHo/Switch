package com.switchpay.ledger;

import com.switchpay.ledger.store.LedgerEntryEntity;
import com.switchpay.ledger.store.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §12.3 invariant 1: debits equal credits per (transaction, currency), enforced by the
 * DEFERRABLE INITIALLY DEFERRED trigger in V4__ledger.sql.
 *
 * These tests must COMMIT. A deferred constraint trigger fires at commit and nowhere else, so
 * the usual rolled-back test transaction never reaches it and flush() cannot substitute:
 * an earlier version of this class asserted on flush() and passed vacuously while proving
 * nothing. TestTransaction.end() performs the commit inline, which is what puts the trigger
 * on the stack where assertThrows can see it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class LedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerRepository ledgerRepository;

    private LedgerEntryEntity entry(UUID transactionId, ChartOfAccounts account, Direction direction, long amount) {
        return new LedgerEntryEntity(transactionId, account.getId(), direction, amount, "USD", UUID.randomUUID(), "Test");
    }

    @Test
    void balancedTransactionCommits() {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
                entry(transactionId, ChartOfAccounts.ACQUIRER_CLEARING, Direction.DEBIT, 100L),
                entry(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE, Direction.CREDIT, 100L)
        ));

        TestTransaction.flagForCommit();
        assertDoesNotThrow(TestTransaction::end);
    }

    @Test
    void unbalancedTransactionIsRejectedAtCommit() {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
                entry(transactionId, ChartOfAccounts.ACQUIRER_CLEARING, Direction.DEBIT, 100L),
                entry(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE, Direction.CREDIT, 99L)
        ));

        // The rows insert happily: nothing is wrong until the transaction tries to become durable.
        ledgerRepository.flush();

        TestTransaction.flagForCommit();
        Throwable thrown = assertThrows(Throwable.class, TestTransaction::end);

        assertThat(rootCauseMessage(thrown))
                .as("the trigger, not some incidental failure, must be what rejected this")
                .contains("unbalanced")
                .contains(transactionId.toString());
    }

    @Test
    void imbalanceAcrossTwoCurrenciesIsRejectedEvenThoughTheMinorUnitsCancel() {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
                new LedgerEntryEntity(transactionId, ChartOfAccounts.ACQUIRER_CLEARING.getId(),
                        Direction.DEBIT, 100L, "USD", null, "USD leg"),
                new LedgerEntryEntity(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE.getId(),
                        Direction.CREDIT, 100L, "EUR", null, "EUR leg")
        ));

        TestTransaction.flagForCommit();
        Throwable thrown = assertThrows(Throwable.class, TestTransaction::end);

        assertThat(rootCauseMessage(thrown)).contains("unbalanced");
    }

    private String rootCauseMessage(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
