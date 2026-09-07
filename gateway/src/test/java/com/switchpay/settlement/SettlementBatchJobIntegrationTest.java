package com.switchpay.settlement;

import com.switchpay.common.Currency;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.ledger.TrialBalanceService;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.store.PaymentOperationRepository;
import com.switchpay.routing.AcquirerClient;
import com.switchpay.routing.RetrySafety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves §13.1's "Done when": a re-run does not double-settle, and the ledger stays balanced.
 * The acquirer HTTP boundary is mocked so the payment routes to a real directory entry,
 * which means the acquirer's cost_bps/cost_fixed_minor genuinely participate in the fee split.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class SettlementBatchJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private SettlementBatchJob settlementBatchJob;
    @Autowired
    private SettlementBatchRepository batchRepository;
    @Autowired
    private PaymentOperationRepository operationRepository;
    @Autowired
    private TrialBalanceService trialBalanceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AcquirerClient acquirerClient;

    @BeforeEach
    void acquirerApproves() {
        when(acquirerClient.authorize(anyString(), any(), anyString())).thenReturn(
                new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE,
                        new AuthorizationResponse("APPROVED", "VN-1", "A1", null), null));
    }

    @Test
    void rerun_does_not_double_settle_and_ledger_stays_balanced() {
        UUID merchantId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO merchant (id, name, api_key_hash, webhook_secret, rate_bps, fixed_fee_minor) VALUES (?, 'Test', 'hash', 'secret', 250, 30)",
            merchantId);
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // Unique per call, not a literal shared across every card this class inserts:
        // VELOCITY_CARD_1H is real (NR-10); see its landmine note.
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
            INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                     bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
            VALUES (?, ?, decode('00', 'hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'US', 12, 2030)
            """, token, merchantId, panFingerprint);

        Payment payment = paymentService.createPayment(merchantId, "ref-" + UUID.randomUUID(), token, Currency.EUR, 10_000);
        paymentService.authorize(payment.id());
        paymentService.capture(payment.id(), 10_000);

        LocalDate businessDate = LocalDate.now();
        settlementBatchJob.runForDate(businessDate);

        // Scoped to this test's own merchant: the class doesn't roll back between tests, so an
        // unscoped findAll() would also see whatever other test methods in this class settled.
        List<SettlementBatchEntity> batches = batchesFor(merchantId);
        assertThat(batches).hasSize(1);
        SettlementBatchEntity batch = batches.get(0);
        assertThat(batch.getMerchantId()).isEqualTo(merchantId);
        assertThat(batch.getGrossMinor()).isEqualTo(10_000);
        assertThat(batch.getGatewayFeeMinor()).isEqualTo(10_000 * 250 / 10000 + 30);
        // A US-issued Visa paying EUR only matches FALLBACK-GLOBAL: 45 bps + 15 fixed (§9.1).
        assertThat(batch.getAcquirerId()).isEqualTo("FALLBACK-GLOBAL");
        assertThat(batch.getSchemeFeeMinor()).isEqualTo(10_000 * 45 / 10000 + 15);
        assertThat(batch.getNetMinor()).isEqualTo(batch.getGrossMinor() - batch.getSchemeFeeMinor() - batch.getGatewayFeeMinor());

        assertThat(operationRepository.findAll())
            .filteredOn(op -> op.getPaymentId().equals(payment.id()) && "CAPTURE".equals(op.getType()))
            .allMatch(op -> "SETTLED".equals(op.getSettlementState()));

        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();

        settlementBatchJob.runForDate(businessDate);

        assertThat(batchesFor(merchantId)).hasSize(1);
    }

    private List<SettlementBatchEntity> batchesFor(UUID merchantId) {
        return batchRepository.findAll().stream()
            .filter(b -> b.getMerchantId().equals(merchantId))
            .toList();
    }

    /**
     * NR-16: the settlement posting used to be gross − fees per item, silently omitting refunds.
     * MERCHANT_RECEIVABLE would carry a permanent residual equal to the refund whenever a window
     * had one, and net_minor (gross − refunds − fees) would no longer match what had actually
     * moved to MERCHANT_PAYABLE. This proves the residual is gone: MERCHANT_RECEIVABLE for this
     * payment must be exactly zero once settlement has run, not merely "the trial balance is
     * still zero somewhere else."
     */
    @Test
    void settlement_nets_out_refunds_so_merchant_receivable_actually_zeroes() {
        UUID merchantId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO merchant (id, name, api_key_hash, webhook_secret, rate_bps, fixed_fee_minor) VALUES (?, 'Test', 'hash', 'secret', 250, 30)",
            merchantId);
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // Unique per call, not a literal shared across every card this class inserts:
        // VELOCITY_CARD_1H is real (NR-10); see its landmine note.
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
            INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                     bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
            VALUES (?, ?, decode('00', 'hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'US', 12, 2030)
            """, token, merchantId, panFingerprint);

        Payment payment = paymentService.createPayment(merchantId, "ref-" + UUID.randomUUID(), token, Currency.EUR, 10_000);
        paymentService.authorize(payment.id());
        paymentService.capture(payment.id(), 10_000);
        paymentService.refund(payment.id(), 4_000);

        LocalDate businessDate = LocalDate.now();
        settlementBatchJob.runForDate(businessDate);

        SettlementBatchEntity batch = batchesFor(merchantId).get(0);
        long schemeFee = 10_000 * 45 / 10000 + 15;
        long gatewayFee = 10_000 * 250 / 10000 + 30;
        assertThat(batch.getRefundsMinor()).isEqualTo(4_000);
        assertThat(batch.getNetMinor()).isEqualTo(10_000 - 4_000 - schemeFee - gatewayFee);

        Long merchantReceivableBalance = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0)
            FROM ledger_entry
            WHERE account_id = '22222222-2222-2222-2222-222222222222' AND payment_id = ?
            """, Long.class, payment.id());
        assertThat(merchantReceivableBalance)
            .as("capture(credit) - refund(debit) - fees(debit) - settlement(debit) must net to exactly zero")
            .isZero();

        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();
    }
}
