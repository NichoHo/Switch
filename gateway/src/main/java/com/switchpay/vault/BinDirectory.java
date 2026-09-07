package com.switchpay.vault;

import java.util.Optional;

public final class BinDirectory {

    public record BinMetadata(String bin, String brand, String fundingType, String issuerCountry) {}

    public static Optional<BinMetadata> lookup(Pan pan) {
        String panStr = pan.getValue();
        if (panStr.length() < 13) {
            return Optional.empty();
        }

        String bin = panStr.substring(0, Math.min(8, panStr.length()));
        // For test cards, we usually match on prefix
        
        if (panStr.startsWith("42424242")) {
            return Optional.of(new BinMetadata(panStr.substring(0, 8), "VISA", "CREDIT", "GB"));
        }
        if (panStr.startsWith("400000")) {
            return Optional.of(new BinMetadata(panStr.substring(0, 8), "VISA", "CREDIT", "US"));
        }
        if (panStr.startsWith("55555555")) {
            return Optional.of(new BinMetadata(panStr.substring(0, 8), "MASTERCARD", "CREDIT", "US"));
        }
        
        return Optional.empty(); // Unrecognised or non-test BIN
    }
}
