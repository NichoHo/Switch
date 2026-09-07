package com.switchpay.payment;

import com.switchpay.common.Currency;
import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.CaptureNotification;
import com.switchpay.ledger.LedgerService;
import com.switchpay.merchant.MerchantEntity;
import com.switchpay.merchant.MerchantRepository;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.domain.PaymentState;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentEventEntity;
import com.switchpay.payment.store.PaymentEventRepository;
import com.switchpay.payment.store.PaymentOperationEntity;
import com.switchpay.payment.store.PaymentOperationRepository;
import com.switchpay.payment.store.PaymentRepository;
import com.switchpay.payment.store.ThreedsChallengeEntity;
import com.switchpay.payment.store.ThreedsChallengeRepository;
import com.switchpay.risk.RiskAssessmentRecorder;
import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskDecision;
import com.switchpay.risk.store.RiskAssessmentRepository;
import com.switchpay.routing.AcquirerClient;
import com.switchpay.routing.AcquirerDirectory;
import com.switchpay.routing.Router;
import com.switchpay.vault.store.CardTokenEntity;
import com.switchpay.vault.store.CardTokenRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;
    private final com.switchpay.risk.RiskService riskService;
    private final ThreedsChallengeRepository threedsChallengeRepository;
    private final com.switchpay.outbox.OutboxRepository outboxRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final LedgerService ledgerService;
    private final AcquirerDirectory acquirerDirectory;
    private final AcquirerClient acquirerClient;
    private final Router router;
    private final CardTokenRepository cardTokenRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final MerchantRepository merchantRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskAssessmentRecorder riskAssessmentRecorder;

    public PaymentService(PaymentRepository paymentRepository, EntityManager entityManager, com.switchpay.risk.RiskService riskService, ThreedsChallengeRepository threedsChallengeRepository, com.switchpay.outbox.OutboxRepository outboxRepository, PaymentOperationRepository paymentOperationRepository, LedgerService ledgerService, AcquirerDirectory acquirerDirectory, AcquirerClient acquirerClient, Router router, CardTokenRepository cardTokenRepository, PaymentEventRepository paymentEventRepository, MerchantRepository merchantRepository, RiskAssessmentRepository riskAssessmentRepository, RiskAssessmentRecorder riskAssessmentRecorder) {
        this.paymentEventRepository = paymentEventRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
        this.riskService = riskService;
        this.threedsChallengeRepository = threedsChallengeRepository;
        this.outboxRepository = outboxRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.ledgerService = ledgerService;
        this.acquirerDirectory = acquirerDirectory;
        this.acquirerClient = acquirerClient;
        this.router = router;
        this.cardTokenRepository = cardTokenRepository;
        this.merchantRepository = merchantRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.riskAssessmentRecorder = riskAssessmentRecorder;
    }

    @Transactional
    public Payment createPayment(UUID merchantId, String merchantRef, String token, Currency currency, long amount) {
        // Checked here, not left to the FK: a token that doesn't exist — or belongs to a
        // different merchant — must fail as a clean domain error, not a raw constraint
        // violation the caller has no code to branch on.
        cardTokenRepository.findByTokenAndMerchantId(token, merchantId)
                .orElseThrow(() -> new IllegalArgumentException("card_token_not_found"));

        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, merchantId, merchantRef, token, amount, currency);
        
        PaymentEntity entity = new PaymentEntity();
        entity.setId(payment.getId());
        entity.setMerchantId(payment.getMerchantId());
        entity.setMerchantReference(payment.getMerchantReference());
        entity.setCardToken(payment.getCardToken());
        entity.setCurrency(payment.getCurrency().name());
        entity.setAmountMinor(payment.getAmountMinor());
        entity.setCapturedAmountMinor(payment.getCapturedAmountMinor());
        entity.setRefundedAmountMinor(payment.getRefundedAmountMinor());
        entity.setState(payment.getState().name());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        
        paymentRepository.save(entity);
        return payment;
    }

    @Transactional
    public void authorize(UUID paymentId) {
        authorize(paymentId, PaymentContext.EMPTY);
    }

    /**
     * §10 — the caller-supplied {@code context} (§16's request example: ip/emailHash/
     * deviceFingerprint) plus the card just resolved is what makes the risk rules real rather
     * than evaluating against literals (NR-10). Thresholds come from the merchant's own
     * {@code deny_threshold}/{@code challenge_threshold}, not a hardcoded 70/40 (NR-9) — the
     * single-arg overload above is the only caller still getting the RiskService demo defaults,
     * and it does so because it has no context or merchant thresholds to offer instead.
     */
    @Transactional
    public void authorize(UUID paymentId, PaymentContext context) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);

        CardTokenEntity card = cardTokenRepository
                .findByTokenAndMerchantId(entity.getCardToken(), entity.getMerchantId())
                .orElseThrow(() -> new IllegalArgumentException("card_token_not_found"));
        String panFingerprint = HexFormat.of().formatHex(card.getPanFingerprint());

        MerchantEntity merchant = merchantRepository.findById(entity.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Payment references a merchant that no longer exists"));

        boolean isNewDevice = context.deviceFingerprint() != null
                && !riskAssessmentRepository.existsByDeviceFingerprint(context.deviceFingerprint());

        RiskContext riskContext = new RiskContext(
                panFingerprint, context.ip(), context.emailHash(),
                card.getIssuerCountry(), context.ipCountry(),
                payment.getAmountMinor(), payment.getMerchantId(), isNewDevice
        );
        RiskDecision decision = riskService.evaluate(
                riskContext, merchant.getDenyThreshold(), merchant.getChallengeThreshold());
        riskAssessmentRecorder.record(paymentId, riskContext, decision, 
                merchant.getDenyThreshold(), merchant.getChallengeThreshold(), decision.rulesetVersion());

        entity.setRiskDecision(decision.action());
        entity.setRiskScore(decision.score());

        if (RiskDecision.DENY.equals(decision.action())) {
            payment.riskDeny();
            savePayment(payment, entity);
            return;
        } else if (RiskDecision.CHALLENGE.equals(decision.action())) {
            payment.riskChallenge();
            savePayment(payment, entity);

            ThreedsChallengeEntity challenge = new ThreedsChallengeEntity();
            challenge.setChallengeId(UUID.randomUUID());
            challenge.setPaymentId(paymentId);
            challenge.setStatus("PENDING");
            challenge.setCreatedAt(Instant.now());
            challenge.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
            threedsChallengeRepository.save(challenge);
            return;
        }

        routeAndApply(payment, entity);
        savePayment(payment, entity);
    }

    /**
     * §3 steps 6–7 and §9. Picks a candidate acquirer, calls it, and maps the outcome onto the
     * aggregate. The idempotency key we send is the payment id, which is the same key
     * {@link com.switchpay.routing.StatusProbeJob} later asks the acquirer about — that
     * correspondence is what makes an AUTH_UNKNOWN resolvable rather than a guess.
     */
    private void routeAndApply(Payment payment, PaymentEntity entity) {
        CardTokenEntity card = cardTokenRepository
                .findByTokenAndMerchantId(entity.getCardToken(), entity.getMerchantId())
                .orElseThrow(() -> new IllegalArgumentException("card_token_not_found"));

        AuthorizationRequest request = new AuthorizationRequest(
                payment.getId().toString(), card.getBin(), card.getLast4(), card.getBrand(),
                card.getFundingType(), card.getIssuerCountry(), entity.getCurrency(),
                entity.getAmountMinor(), entity.getMerchantId().toString(), entity.getMerchantReference());

        Router.RoutingResult result = router.route(
                request, card.getBrand(), entity.getCurrency(), card.getIssuerCountry());

        // Recorded before the branch: an AUTH_UNKNOWN with no acquirer id is unprobeable, which
        // is the one state where losing the attribution actually costs money.
        entity.setAcquirerId(result.acquirerId());

        switch (result.resultState()) {
            case AUTHORIZED -> {
                entity.setAcquirerReference(result.response().acquirerReference());
                entity.setAuthCode(result.response().authCode());
                payment.authorize(result.acquirerId(), result.response().acquirerReference(),
                        result.response().authCode(), Instant.now().plus(7, ChronoUnit.DAYS));
            }
            case AUTH_DECLINED -> {
                if (result.response() != null) {
                    entity.setAcquirerReference(result.response().acquirerReference());
                }
                payment.authDecline();
            }
            case AUTH_UNKNOWN -> payment.authUnknown();
            default -> throw new IllegalStateException("Unroutable outcome: " + result.resultState());
        }
    }

    @Transactional
    public void capture(UUID paymentId, long amount) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);
        payment.capture(amount);

        savePayment(payment, entity);
        recordSettlementOperation(entity, "CAPTURE", amount);
    }

    @Transactional
    public void voidPayment(UUID paymentId) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);
        payment.voidPayment();

        savePayment(payment, entity);
    }

    public PaymentEntity getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));
    }

    /** NR-14: the read a controller needs to render §16's `card` block, without holding a repository itself. */
    public record PaymentWithCard(PaymentEntity payment, CardTokenEntity card) {}

    public PaymentWithCard withCard(PaymentEntity entity) {
        CardTokenEntity card = cardTokenRepository
                .findByTokenAndMerchantId(entity.getCardToken(), entity.getMerchantId())
                .orElse(null);
        return new PaymentWithCard(entity, card);
    }

    /** The most recent 3DS challenge for a payment, if it has one — for building the §11 step 2 redirect. */
    public java.util.Optional<UUID> findPendingChallengeId(UUID paymentId) {
        return threedsChallengeRepository.findFirstByPaymentIdOrderByCreatedAtDesc(paymentId)
                .map(ThreedsChallengeEntity::getChallengeId);
    }

    public List<PaymentEventEntity> getEvents(UUID paymentId) {
        return paymentEventRepository.findByPaymentIdOrderBySeqAsc(paymentId);
    }

    // filters applied in Java over one merchant's payments. Push into the query the
    // day a merchant has enough history for the list to matter.
    public List<PaymentEntity> listPayments(UUID merchantId, String state, String merchantReference) {
        return paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .filter(p -> state == null || state.equalsIgnoreCase(p.getState()))
                .filter(p -> merchantReference == null || merchantReference.equals(p.getMerchantReference()))
                .toList();
    }

    @Transactional
    public void refund(UUID paymentId, long amount) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);
        payment.refund(amount);

        savePayment(payment, entity);
        recordSettlementOperation(entity, "REFUND", amount);
    }

    /**
     * Persists the operation-level record that settlement batches over and posts the
     * corresponding ledger entry. Capture/refund on the aggregate move money nowhere by
     * themselves (§12.2) — this is where they become real bookkeeping.
     */
    private void recordSettlementOperation(PaymentEntity entity, String type, long amount) {
        PaymentOperationEntity op = new PaymentOperationEntity();
        op.setId(UUID.randomUUID());
        op.setPaymentId(entity.getId());
        op.setType(type);
        op.setAmountMinor(amount);
        op.setCurrency(entity.getCurrency());
        op.setState("COMPLETED");
        op.setAcquirerId(entity.getAcquirerId());
        op.setAcquirerRef(entity.getAcquirerReference());
        op.setCreatedAt(Instant.now());
        paymentOperationRepository.save(op);

        if ("CAPTURE".equals(type)) {
            ledgerService.recordCapture(entity.getId(), amount, entity.getCurrency());
            scheduleAcquirerCaptureNotification(entity, op);
        } else {
            ledgerService.recordRefund(entity.getId(), amount, entity.getCurrency());
        }
    }

    /**
     * NR-17: this used to call the acquirer inline, holding the payment's
     * {@code PESSIMISTIC_WRITE} row lock for the duration of a network call. The capture itself
     * is already durable at this point in the method — nothing downstream depends on the
     * notification landing before the transaction ends — so it runs {@code afterCommit()} instead,
     * outside the lock. Best-effort either way: a missed notification simply surfaces as a
     * {@code MISSING_AT_ACQUIRER} recon exception later, which is the correct failure mode for an
     * acquirer that is temporarily unreachable.
     */
    private void scheduleAcquirerCaptureNotification(PaymentEntity entity, PaymentOperationEntity op) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyAcquirerOfCapture(entity, op);
            }
        });
    }

    private void notifyAcquirerOfCapture(PaymentEntity entity, PaymentOperationEntity op) {
        acquirerDirectory.findById(entity.getAcquirerId()).ifPresent(acquirer -> {
            try {
                acquirerClient.notifyCapture(acquirer.baseUrl(), new CaptureNotification(
                    op.getId().toString(), op.getAmountMinor(), op.getCurrency(),
                    LocalDate.now(ZoneOffset.UTC).toString()));
            } catch (Exception e) {
                logger.warn("Capture notification to acquirer {} failed for operation {}: {}",
                    entity.getAcquirerId(), op.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    public void resolveAuthUnknown(UUID paymentId, boolean approved) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);
        payment.probeResolved(approved);
        
        savePayment(payment, entity);
    }

    @Transactional
    public void handleThreedsCallback(UUID paymentId, boolean success) {
        PaymentEntity entity = paymentRepository.findAndLockById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment_not_found"));

        Payment payment = mapToDomain(entity);
        if (success) {
            payment.authenticateOk();
            savePayment(payment, entity);
            // AUTHENTICATE_OK returns the payment to CREATED (§6.2); the ordinary authorize path
            // then runs, so a 3DS-cleared payment routes exactly like any other.
            routeAndApply(payment, entity);
            savePayment(payment, entity);
        } else {
            payment.authenticateFail();
            savePayment(payment, entity);
        }
    }

    /**
     * §11 step 4/5, the business logic behind {@code ThreedsCallbackController} (NR-14). The
     * controller's job stops at reading the raw body and verifying the signature — both
     * genuinely HTTP-layer concerns — and starts here: challenge-id binding, state and
     * expiry checks, and driving the payment through the result.
     *
     * @return whether the assertion was a successful authentication
     */
    @Transactional
    public boolean completeThreedsChallenge(UUID challengeId, UUID signedChallengeId, String status) {
        if (!signedChallengeId.equals(challengeId)) {
            // The signature is valid but for a *different* challenge — someone is replaying a
            // genuine assertion against the wrong URL. Reject rather than trust the path alone.
            throw new IllegalArgumentException("threeds_challenge_id_mismatch");
        }

        ThreedsChallengeEntity challenge = threedsChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("threeds_challenge_not_found"));
        if (!"PENDING".equals(challenge.getStatus())) {
            throw new IllegalStateException("threeds_challenge_already_resolved");
        }
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("threeds_challenge_expired");
        }

        challenge.setStatus(status);
        threedsChallengeRepository.save(challenge);

        boolean success = "SUCCESS".equalsIgnoreCase(status);
        handleThreedsCallback(challenge.getPaymentId(), success);
        return success;
    }



    /**
     * NR-3: this used to build a fresh {@code CREATED} aggregate and then {@code setAccessible(true)}
     * its way into five private fields — the exact FSM protection §19.1 exists to prove was
     * bypassable by anything in the same JVM, not just this class. {@link Payment#reconstitute}
     * is the real, encapsulated rehydration path; this method's only job now is the entity↔domain
     * type mapping.
     */
    private Payment mapToDomain(PaymentEntity entity) {
        return Payment.reconstitute(
                entity.getId(),
                entity.getMerchantId(),
                entity.getMerchantReference(),
                entity.getCardToken(),
                entity.getAmountMinor(),
                Currency.valueOf(entity.getCurrency()),
                PaymentState.valueOf(entity.getState()),
                entity.getCapturedAmountMinor(),
                entity.getRefundedAmountMinor(),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.isLiabilityShift()
        );
    }

    private void savePayment(Payment payment, PaymentEntity entity) {
        entity.setState(payment.getState().name());
        entity.setCapturedAmountMinor(payment.getCapturedAmountMinor());
        entity.setRefundedAmountMinor(payment.getRefundedAmountMinor());
        entity.setUpdatedAt(Instant.now());
        entity.setLiabilityShift(payment.isLiabilityShift());
        paymentRepository.save(entity);

        Integer maxSeq = entityManager.createQuery(
                "SELECT MAX(e.seq) FROM PaymentEventEntity e WHERE e.paymentId = :paymentId", Integer.class)
            .setParameter("paymentId", payment.getId())
            .getSingleResult();
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (var event : payment.getEvents()) {
            PaymentEventEntity evEntity = new PaymentEventEntity();
            evEntity.setPaymentId(payment.getId());
            evEntity.setSeq(seq++);
            evEntity.setFromState(event.fromState().name());
            evEntity.setToState(event.toState().name());
            evEntity.setActor(event.actor());
            evEntity.setReasonCode(event.reasonCode());
            evEntity.setCreatedAt(Instant.now());
            entityManager.persist(evEntity);

            com.switchpay.outbox.OutboxEventEntity outboxEvent = new com.switchpay.outbox.OutboxEventEntity();
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setMerchantId(payment.getMerchantId());
            outboxEvent.setEventType("payment." + event.toState().name().toLowerCase());
            outboxEvent.setPayload("{\"payment_id\": \"" + payment.getId() + "\", \"state\": \"" + event.toState().name() + "\"}");
            outboxEvent.setState("PENDING");
            outboxEvent.setAttempts(0);
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxEvent.setCreatedAt(Instant.now());
            outboxRepository.save(outboxEvent);
        }
        payment.getEvents().clear();
    }
}
