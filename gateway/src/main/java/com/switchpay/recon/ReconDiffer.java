package com.switchpay.recon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure diff of the gateway's internal captures against the acquirer's settlement file.
 * No Spring, no JPA — proven by feeding it hand-built lists, one discrepancy type per test.
 */
public final class ReconDiffer {
    private ReconDiffer() {}

    public static List<ReconFinding> diff(List<InternalCaptureRecord> internal, List<SettlementFileRow> file, LocalDate expectedDate) {
        Map<String, InternalCaptureRecord> byReference = new HashMap<>();
        for (InternalCaptureRecord record : internal) {
            byReference.put(record.reference(), record);
        }

        List<ReconFinding> findings = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        Set<String> matched = new HashSet<>();

        for (SettlementFileRow row : file) {
            if (!seenInFile.add(row.reference())) {
                findings.add(new ReconFinding(ReconDiscrepancyType.DUPLICATE_AT_ACQUIRER, row.reference(),
                    null, null, null, row.amountMinor(), null, row.currency(),
                    "Reference " + row.reference() + " appears more than once in the settlement file"));
                continue;
            }

            InternalCaptureRecord match = byReference.get(row.reference());
            if (match == null) {
                findings.add(new ReconFinding(ReconDiscrepancyType.UNKNOWN_AT_GATEWAY, row.reference(),
                    null, null, null, row.amountMinor(), null, row.currency(),
                    "Reference " + row.reference() + " is in the settlement file but unknown internally"));
                continue;
            }
            matched.add(row.reference());

            if (match.amountMinor() != row.amountMinor()) {
                findings.add(new ReconFinding(ReconDiscrepancyType.AMOUNT_MISMATCH, row.reference(),
                    match.paymentId(), match.acquirerId(), match.amountMinor(), row.amountMinor(),
                    match.currency(), row.currency(),
                    "Internal amount " + match.amountMinor() + " != file amount " + row.amountMinor()));
            }
            if (!match.currency().equals(row.currency())) {
                findings.add(new ReconFinding(ReconDiscrepancyType.CURRENCY_MISMATCH, row.reference(),
                    match.paymentId(), match.acquirerId(), match.amountMinor(), row.amountMinor(),
                    match.currency(), row.currency(),
                    "Internal currency " + match.currency() + " != file currency " + row.currency()));
            }
            if (!row.businessDate().equals(expectedDate)) {
                findings.add(new ReconFinding(ReconDiscrepancyType.DATE_OUT_OF_WINDOW, row.reference(),
                    match.paymentId(), match.acquirerId(), match.amountMinor(), row.amountMinor(),
                    match.currency(), row.currency(),
                    "Settled date " + row.businessDate() + " outside expected window " + expectedDate));
            }
        }

        for (InternalCaptureRecord record : internal) {
            if (!matched.contains(record.reference())) {
                findings.add(new ReconFinding(ReconDiscrepancyType.MISSING_AT_ACQUIRER, record.reference(),
                    record.paymentId(), record.acquirerId(), record.amountMinor(), null,
                    record.currency(), null,
                    "Reference " + record.reference() + " recorded internally but absent from the settlement file"));
            }
        }

        return findings;
    }
}
