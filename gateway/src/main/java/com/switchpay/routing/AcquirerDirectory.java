package com.switchpay.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AcquirerDirectory {
    public record AcquirerEntry(
        String id, Set<String> brands, Set<String> currencies,
        Set<String> issuerCountries, int priority, int costBps,
        boolean enabled, String baseUrl, long costFixedMinor
    ) {}

    private final List<AcquirerEntry> acquirers;

    @Autowired
    public AcquirerDirectory(@Value("${acquirer.sim.url:http://localhost:8081}") String simUrl) {
        this.acquirers = List.of(
            new AcquirerEntry("VISA-NET-EU", Set.of("VISA"), Set.of("EUR", "GBP"), Set.of("FR", "DE", "GB", "NL", "BE", "IT", "ES"), 10, 25, true, simUrl, 10),
            new AcquirerEntry("MC-CLEAR-EU", Set.of("MASTERCARD"), Set.of("EUR", "GBP"), Set.of("FR", "DE", "GB", "NL", "BE", "IT", "ES"), 10, 28, true, simUrl, 10),
            new AcquirerEntry("VISA-NET-US", Set.of("VISA"), Set.of("USD"), Set.of("US"), 20, 30, true, simUrl, 10),
            new AcquirerEntry("FALLBACK-GLOBAL", Set.of("VISA", "MASTERCARD"), Set.of("EUR", "GBP", "USD"), Set.of("*"), 90, 45, true, simUrl, 15)
        );
    }
    
    AcquirerDirectory(List<AcquirerEntry> acquirers) {
        this.acquirers = acquirers;
    }
    
    public List<AcquirerEntry> findCandidates(String brand, String currency, String issuerCountry) {
        return acquirers.stream()
            .filter(AcquirerEntry::enabled)
            .filter(a -> a.brands().contains("*") || a.brands().contains(brand))
            .filter(a -> a.currencies().contains("*") || a.currencies().contains(currency))
            .filter(a -> a.issuerCountries().contains("*") || a.issuerCountries().contains(issuerCountry))
            .sorted(Comparator.comparingInt(AcquirerEntry::priority)
                              .thenComparingInt(AcquirerEntry::costBps))
            .collect(Collectors.toList());
    }

    /** Every configured acquirer, enabled or not. The console lists them all. */
    public List<AcquirerEntry> findAll() {
        return List.copyOf(acquirers);
    }

    public java.util.Optional<AcquirerEntry> findById(String id) {
        return acquirers.stream().filter(a -> a.id().equals(id)).findFirst();
    }
}
