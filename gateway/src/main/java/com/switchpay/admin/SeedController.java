package com.switchpay.admin;

import com.switchpay.common.Currency;
import com.switchpay.dispute.DisputeService;
import com.switchpay.merchant.MerchantEntity;
import com.switchpay.merchant.MerchantService;
import com.switchpay.payment.PaymentContext;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.domain.PaymentState;
import com.switchpay.risk.RiskRuleMode;
import com.switchpay.risk.RulesetService;
import com.switchpay.settlement.SettlementBatchJob;
import com.switchpay.vault.CardVaultService;
import com.switchpay.vault.store.CardTokenEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a demo environment for the operator console: a fixed script of payments that
 * exercises every page, not random load. Each scenario goes through the real services, so
 * routing, risk scoring, the ledger and settlement all produce genuine rows.
 *
 * The script is deterministic. A second call is a no-op, because the demo merchant already exists.
 * To start over, drop the database (`docker compose down -v`).
 */
@RestController
@RequestMapping("/admin")
public class SeedController {

    private static final Logger logger = LoggerFactory.getLogger(SeedController.class);

    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String RULESET = "v2-seed";

    private enum Outcome { CAPTURE, PARTIAL_CAPTURE, REFUND_PARTIAL, REFUND_FULL, DISPUTE, VOID, HOLD }

    private enum Challenge { PASS, FAIL, PENDING }

    /** One row of the demo script. {@code card} keys are "<type>-<letter>", see {@link #pan}. */
    private record Scenario(String ref, String card, Currency currency, long amount, int daysAgo,
                            String ipCountry, String ip, String device, Outcome outcome, Challenge challenge) {}

    private record Placed(UUID paymentId, Instant at) {}

    /*
     * Card types, chosen so the four acquirers all get traffic (see AcquirerDirectory):
     *   GB = Visa, GB issuer    -> VISA-NET-EU for EUR/GBP, FALLBACK-GLOBAL for USD
     *   US = Visa, US issuer    -> VISA-NET-US for USD
     *   DE = Mastercard, DE     -> MC-CLEAR-EU for EUR/GBP
     *   MU = Mastercard, US     -> FALLBACK-GLOBAL (no EU or US Mastercard acquirer matches)
     *
     * Risk, with the v2-seed ruleset and the merchant thresholds 40 (challenge) and 70 (deny):
     *   country mismatch +20, 1h card velocity +7 per earlier use, new device on >500.00 +30,
     *   blocklisted IP or KP country +100.
     */
    private static final List<Scenario> SCRIPT = List.of(
            // Two days ago
            sale("ORD-20417", "GB-A", Currency.EUR, 12_900, 2, "GB", Outcome.CAPTURE),
            sale("ORD-20418", "GB-B", Currency.GBP, 4_850, 2, "GB", Outcome.CAPTURE),
            sale("ORD-20419", "US-E", Currency.USD, 23_400, 2, "US", Outcome.CAPTURE),
            sale("ORD-20420", "US-F", Currency.USD, 8_999, 2, "US", Outcome.REFUND_PARTIAL),
            sale("ORD-20421", "DE-H", Currency.EUR, 15_000, 2, "DE", Outcome.CAPTURE),
            sale("ORD-20422", "GB-C", Currency.USD, 6_200, 2, "DE", Outcome.CAPTURE),
            // Yesterday
            sale("ORD-20431", "GB-A", Currency.EUR, 7_450, 1, "GB", Outcome.CAPTURE),
            sale("ORD-20432", "US-E", Currency.USD, 31_000, 1, "US", Outcome.DISPUTE),
            challenged("ORD-20433", "GB-D", Currency.GBP, 52_000, 1, "US", "dev-7f3a", Challenge.PASS, Outcome.CAPTURE),
            challenged("ORD-20434", "US-G", Currency.USD, 64_900, 1, "GB", "dev-2b9e", Challenge.PASS, Outcome.PARTIAL_CAPTURE),
            sale("ORD-20435", "MU-I", Currency.USD, 9_900, 1, "US", Outcome.REFUND_FULL),
            sale("ORD-20436", "GB-B", Currency.GBP, 2_300, 1, "GB", Outcome.VOID),
            sale("ORD-20437", "US-F", Currency.USD, 18_000, 1, "KP", Outcome.HOLD),
            // Today, oldest first. The console lists newest first, so the top of the Payments
            // page shows the widest spread of states.
            sale("ORD-20448", "GB-J", Currency.EUR, 19_900, 0, "GB", Outcome.CAPTURE),
            sale("ORD-20449", "US-G", Currency.USD, 4_200, 0, "US", Outcome.CAPTURE),
            sale("ORD-20450", "DE-H", Currency.GBP, 11_750, 0, "DE", Outcome.REFUND_PARTIAL),
            sale("ORD-20451", "GB-K", Currency.EUR, 48_500, 0, "NL", Outcome.HOLD),
            sale("ORD-20452", "US-L", Currency.USD, 27_600, 0, "US", Outcome.HOLD),
            challenged("ORD-20453", "GB-M", Currency.GBP, 72_000, 0, "US", "dev-c91b", Challenge.FAIL, Outcome.HOLD),
            challenged("ORD-20454", "US-N", Currency.USD, 88_000, 0, "CA", "dev-4d10", Challenge.PENDING, Outcome.HOLD),
            new Scenario("ORD-20455", "GB-C", Currency.USD, 3_100, 0, "GB", "192.168.1.100", null, Outcome.HOLD, null),
            sale("ORD-20456", "GB-J", Currency.EUR, 6_400, 0, "GB", Outcome.CAPTURE),
            sale("ORD-20458", "GB-A", Currency.EUR, 3_900, 0, "FR", Outcome.CAPTURE)
    );

    private final MerchantService merchantService;
    private final PaymentService paymentService;
    private final RulesetService rulesetService;
    private final CardVaultService cardVaultService;
    private final DisputeService disputeService;
    private final SettlementBatchJob settlementBatchJob;
    private final JdbcTemplate jdbc;

    public SeedController(MerchantService merchantService, PaymentService paymentService,
                          RulesetService rulesetService, CardVaultService cardVaultService,
                          DisputeService disputeService, SettlementBatchJob settlementBatchJob,
                          JdbcTemplate jdbc) {
        this.merchantService = merchantService;
        this.paymentService = paymentService;
        this.rulesetService = rulesetService;
        this.cardVaultService = cardVaultService;
        this.disputeService = disputeService;
        this.settlementBatchJob = settlementBatchJob;
        this.jdbc = jdbc;
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public void seed() {
        if (merchantService.exists(MERCHANT_ID)) {
            return;
        }

        try {
            rulesetService.createRuleset(RULESET, Map.of(
                    "VELOCITY_CARD_1H", RiskRuleMode.ACTIVE,
                    "BIN_COUNTRY_MISMATCH", RiskRuleMode.ACTIVE,
                    "HIGH_RISK_COUNTRY", RiskRuleMode.ACTIVE,
                    "BLOCKLIST_IP", RiskRuleMode.ACTIVE,
                    "NEW_DEVICE_HIGH_AMOUNT", RiskRuleMode.ACTIVE
            ), true);
        } catch (IllegalArgumentException e) {
            // Already exists, ignore
        }

        MerchantEntity merchant = new MerchantEntity();
        merchant.setId(MERCHANT_ID);
        merchant.setName("Northwind Goods");
        merchant.setApiKeyHash("hashed-key");
        merchant.setWebhookSecret("demo-secret");
        merchant.setDenyThreshold(70);
        merchant.setChallengeThreshold(40);
        // The entity's primitives would otherwise overwrite the column defaults with 0,
        // and every settlement batch would show zero gateway fee.
        merchant.setRateBps(250);
        merchant.setFixedFeeMinor(30);
        merchant.setCreatedAt(Instant.now());
        merchantService.save(merchant);

        // Risk velocity counts assessments by their real timestamp, so play the whole script
        // first and move the timestamps afterwards.
        Map<String, CardTokenEntity> tokens = new HashMap<>();
        List<Placed> placed = new ArrayList<>();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i < SCRIPT.size(); i++) {
            Scenario s = SCRIPT.get(i);
            try {
                UUID id = play(s, i, tokens);
                placed.add(new Placed(id, placeAt(s, i, now, today)));
            } catch (Exception e) {
                logger.warn("Seed scenario {} failed: {}", s.ref(), e.toString());
            }
        }

        placed.forEach(this::backdate);

        // Settle the two finished days, as the nightly job would have. Today stays open and is
        // picked up by tonight's run.
        settlementBatchJob.runForDate(today.minusDays(2));
        settlementBatchJob.runForDate(today.minusDays(1));
        jdbc.update("UPDATE settlement_batch SET created_at = LEAST("
                + "(business_date + 1)::timestamp AT TIME ZONE 'UTC' + interval '2 hours', now())");
    }

    private UUID play(Scenario s, int index, Map<String, CardTokenEntity> tokens) {
        CardTokenEntity card = tokens.computeIfAbsent(s.card(),
                key -> cardVaultService.tokenize(MERCHANT_ID, pan(key), (short) 12, (short) 2030));

        Payment payment = paymentService.createPayment(
                MERCHANT_ID, s.ref(), card.getToken(), s.currency(), s.amount());
        UUID id = payment.getId();

        String ip = s.ip() != null ? s.ip() : "203.0.113." + (10 + index);
        paymentService.authorize(id, new PaymentContext(ip, "cust-" + s.ref() + "@example.com", s.device(), s.ipCountry()));

        if (state(id) == PaymentState.AUTHENTICATION_PENDING
                && s.challenge() != null && s.challenge() != Challenge.PENDING) {
            UUID challengeId = paymentService.findPendingChallengeId(id)
                    .orElseThrow(() -> new IllegalStateException("no 3DS challenge for " + s.ref()));
            paymentService.completeThreedsChallenge(challengeId, challengeId,
                    s.challenge() == Challenge.PASS ? "SUCCESS" : "FAILED");
        }

        if (state(id) != PaymentState.AUTHORIZED) {
            return id; // denied, awaiting or failed 3DS: nothing further to do
        }

        long amount = s.amount();
        switch (s.outcome()) {
            case CAPTURE -> paymentService.capture(id, amount);
            case PARTIAL_CAPTURE -> paymentService.capture(id, amount - amount / 4);
            case REFUND_PARTIAL -> {
                paymentService.capture(id, amount);
                paymentService.refund(id, amount * 3 / 10);
            }
            case REFUND_FULL -> {
                paymentService.capture(id, amount);
                paymentService.refund(id, amount);
            }
            case DISPUTE -> {
                paymentService.capture(id, amount);
                disputeService.open(id, "10.4", amount, s.currency().name());
            }
            case VOID -> paymentService.voidPayment(id);
            case HOLD -> { }
        }
        return id;
    }

    private PaymentState state(UUID paymentId) {
        return PaymentState.valueOf(paymentService.getPayment(paymentId).getState());
    }

    /** Today's payments trail back from now. Earlier days land between 09:00 and 18:00 UTC. */
    private static Instant placeAt(Scenario s, int index, Instant now, LocalDate today) {
        if (s.daysAgo() == 0) {
            Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant t = now.minus(Duration.ofMinutes(6L * (SCRIPT.size() - index)));
            return t.isBefore(startOfToday) ? startOfToday.plusSeconds(60L * (index + 1)) : t;
        }
        return today.minusDays(s.daysAgo()).atStartOfDay(ZoneOffset.UTC).toInstant()
                .plus(Duration.ofMinutes(540 + (index * 53L) % 540));
    }

    /** Moves every row of one payment to its scripted time. Nothing may land in the future. */
    private void backdate(Placed p) {
        OffsetDateTime at = p.at().atOffset(ZoneOffset.UTC);
        UUID id = p.paymentId();

        jdbc.update("UPDATE payment SET created_at = ?, "
                + "updated_at = LEAST(?::timestamptz + interval '25 minutes', now()) WHERE id = ?", at, at, id);
        jdbc.update("UPDATE payment_event SET created_at = "
                + "LEAST(?::timestamptz + (seq - 1) * interval '9 minutes', now()) WHERE payment_id = ?", at, id);
        jdbc.update("UPDATE risk_assessment SET created_at = ? WHERE payment_id = ?", at, id);
        jdbc.update("UPDATE payment_operation SET created_at = LEAST(?::timestamptz + "
                + "CASE WHEN type = 'REFUND' THEN interval '3 hours' ELSE interval '20 minutes' END, now()) "
                + "WHERE payment_id = ?", at, id);
        jdbc.update("UPDATE dispute SET opened_at = LEAST(?::timestamptz + interval '6 hours', now()), "
                + "created_at = LEAST(?::timestamptz + interval '6 hours', now()), "
                + "updated_at = LEAST(?::timestamptz + interval '6 hours', now()), "
                + "evidence_due_at = LEAST(?::timestamptz + interval '6 hours', now()) + interval '14 days' "
                + "WHERE payment_id = ?", at, at, at, at, id);
    }

    private static Scenario sale(String ref, String card, Currency currency, long amount, int daysAgo,
                                 String ipCountry, Outcome outcome) {
        return new Scenario(ref, card, currency, amount, daysAgo, ipCountry, null, null, outcome, null);
    }

    private static Scenario challenged(String ref, String card, Currency currency, long amount, int daysAgo,
                                       String ipCountry, String device, Challenge challenge, Outcome outcome) {
        return new Scenario(ref, card, currency, amount, daysAgo, ipCountry, null, device, outcome, challenge);
    }

    /**
     * A Luhn-valid 16 digit test PAN for a card key such as "GB-A". The type picks a prefix the
     * BinDirectory knows; the letter makes the number unique within that type.
     */
    private static String pan(String key) {
        String prefix = switch (key.substring(0, 2)) {
            case "GB" -> "42424242";
            case "US" -> "400000";
            case "DE" -> "52008282";
            case "MU" -> "55555555";
            default -> throw new IllegalArgumentException("unknown card type " + key);
        };
        int bodyLength = 15 - prefix.length();
        long modulus = (long) Math.pow(10, bodyLength);
        long n = key.charAt(3) - 'A' + 1;
        String partial = prefix + String.format("%0" + bodyLength + "d", (n * 7_919_311L + 104_729L) % modulus);
        return partial + luhnCheckDigit(partial);
    }

    private static int luhnCheckDigit(String partial) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int digit = partial.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }
}
