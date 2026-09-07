package com.switchpay.recon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** `reference,amount_minor,currency,business_date`: plain CSV, no library needed for four columns. */
public final class SettlementFileParser {
    private SettlementFileParser() {}

    public static List<SettlementFileRow> parse(String csv) {
        List<SettlementFileRow> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.split("\n");
        for (int i = 1; i < lines.length; i++) {  // skip header
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",");
            rows.add(new SettlementFileRow(cols[0], Long.parseLong(cols[1]), cols[2], LocalDate.parse(cols[3])));
        }
        return rows;
    }
}
