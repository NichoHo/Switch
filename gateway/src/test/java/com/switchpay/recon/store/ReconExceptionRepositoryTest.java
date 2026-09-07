package com.switchpay.recon.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class ReconExceptionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReconExceptionRepository repository;

    @Test
    void persists_and_resolves_never_deleting() {
        ReconExceptionEntity exception = new ReconExceptionEntity(
            UUID.randomUUID(), LocalDate.of(2026, 8, 7), "VISA-NET-EU", "AMOUNT_MISMATCH", "HIGH",
            "op-1", 1000L, 1050L, "EUR", "EUR", "Internal amount 1000 != file amount 1050");
        repository.save(exception);

        assertThat(repository.findByResolvedAtIsNull()).hasSize(1);
        assertThat(repository.countByTypeAndResolvedAtIsNull("AMOUNT_MISMATCH")).isEqualTo(1);

        ReconExceptionEntity found = repository.findById(exception.getId()).orElseThrow();
        found.resolve("ops-user", "Confirmed manual capture retry, amount corrected upstream");
        repository.save(found);

        assertThat(repository.findByResolvedAtIsNull()).isEmpty();
        assertThat(repository.findById(exception.getId())).isPresent();
        assertThat(repository.findById(exception.getId()).orElseThrow().getResolvedBy()).isEqualTo("ops-user");
    }
}
