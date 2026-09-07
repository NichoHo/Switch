package com.switchpay.acqsim;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class SettlementFileController {

    private final CaptureStore captureStore;
    private final SettlementFaultInjector faultInjector;

    public SettlementFileController(CaptureStore captureStore, SettlementFaultInjector faultInjector) {
        this.captureStore = captureStore;
        this.faultInjector = faultInjector;
    }

    @GetMapping(value = "/settlement-files/{date}.csv", produces = "text/csv")
    public ResponseEntity<String> settlementFile(@PathVariable String date) {
        SettlementFaultInjector.FaultConfig cfg = faultInjector.getConfig();
        List<CaptureStore.Capture> captures = captureStore.forDate(date);

        // Each fault type fires at most once per file so a live demo shows one clear example at a time.
        boolean dropped = false, mismatched = false, currencyMismatched = false, dateShifted = false, duplicated = false;
        List<String[]> rows = new ArrayList<>();

        for (CaptureStore.Capture c : captures) {
            if (!dropped && roll(cfg.dropRatePercent())) {
                dropped = true;
                continue;
            }
            long amount = c.amountMinor();
            String currency = c.currency();
            String rowDate = c.businessDate();

            if (!mismatched && roll(cfg.amountMismatchRatePercent())) {
                amount += 100;
                mismatched = true;
            }
            if (!currencyMismatched && roll(cfg.currencyMismatchRatePercent())) {
                currency = "EUR".equals(currency) ? "USD" : "EUR";
                currencyMismatched = true;
            }
            if (!dateShifted && roll(cfg.dateOutOfWindowRatePercent())) {
                rowDate = LocalDate.parse(rowDate).plusDays(5).toString();
                dateShifted = true;
            }

            rows.add(new String[]{c.reference(), String.valueOf(amount), currency, rowDate});

            if (!duplicated && roll(cfg.duplicateRatePercent())) {
                rows.add(new String[]{c.reference(), String.valueOf(amount), currency, rowDate});
                duplicated = true;
            }
        }

        if (roll(cfg.phantomRatePercent())) {
            rows.add(new String[]{"unknown-" + UUID.randomUUID(), "9999", "EUR", date});
        }

        StringBuilder csv = new StringBuilder("reference,amount_minor,currency,business_date\n");
        for (String[] row : rows) {
            csv.append(String.join(",", row)).append('\n');
        }

        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(csv.toString());
    }

    private boolean roll(int percent) {
        return percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent;
    }
}
