package com.switchpay.recon;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §13.2 — each of the six discrepancy types gets its own test, injecting exactly that fault
 * and asserting exactly that exception type (and no others) is raised. Pure domain logic:
 * no Spring, no database.
 */
public class ReconDifferTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 7);

    private InternalCaptureRecord internalCapture(String ref, long amount, String currency) {
        return new InternalCaptureRecord(ref, amount, currency, DATE, UUID.randomUUID(), "VISA-NET-EU");
    }

    private SettlementFileRow fileRow(String ref, long amount, String currency, LocalDate date) {
        return new SettlementFileRow(ref, amount, currency, date);
    }

    @Test
    void matching_records_produce_no_findings() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");
        SettlementFileRow file = fileRow("op-1", 1000, "EUR", DATE);

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(file), DATE);

        assertThat(findings).isEmpty();
    }

    @Test
    void present_internally_absent_in_file_is_missing_at_acquirer() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.MISSING_AT_ACQUIRER);
        assertThat(findings.get(0).reference()).isEqualTo("op-1");
    }

    @Test
    void present_in_file_absent_internally_is_unknown_at_gateway() {
        SettlementFileRow file = fileRow("op-ghost", 1000, "EUR", DATE);

        List<ReconFinding> findings = ReconDiffer.diff(List.of(), List.of(file), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.UNKNOWN_AT_GATEWAY);
    }

    @Test
    void same_reference_different_amount_is_amount_mismatch() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");
        SettlementFileRow file = fileRow("op-1", 1050, "EUR", DATE);

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(file), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.AMOUNT_MISMATCH);
        assertThat(findings.get(0).internalAmountMinor()).isEqualTo(1000L);
        assertThat(findings.get(0).fileAmountMinor()).isEqualTo(1050L);
    }

    @Test
    void same_reference_twice_in_file_is_duplicate_at_acquirer() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");
        SettlementFileRow file = fileRow("op-1", 1000, "EUR", DATE);

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(file, file), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.DUPLICATE_AT_ACQUIRER);
    }

    @Test
    void different_currency_is_currency_mismatch() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");
        SettlementFileRow file = fileRow("op-1", 1000, "USD", DATE);

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(file), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.CURRENCY_MISMATCH);
    }

    @Test
    void settled_date_outside_window_is_date_out_of_window() {
        InternalCaptureRecord internal = internalCapture("op-1", 1000, "EUR");
        SettlementFileRow file = fileRow("op-1", 1000, "EUR", DATE.plusDays(5));

        List<ReconFinding> findings = ReconDiffer.diff(List.of(internal), List.of(file), DATE);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(ReconDiscrepancyType.DATE_OUT_OF_WINDOW);
    }
}
