package com.switchpay.acqsim;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** What the acquirer "actually processed" — the source of truth the settlement file is built from. */
@Component
public class CaptureStore {

    public record Capture(String reference, long amountMinor, String currency, String businessDate) {}

    private final Map<String, List<Capture>> byDate = new ConcurrentHashMap<>();

    public void record(Capture capture) {
        byDate.computeIfAbsent(capture.businessDate(), d -> new CopyOnWriteArrayList<>()).add(capture);
    }

    public List<Capture> forDate(String businessDate) {
        return byDate.getOrDefault(businessDate, List.of());
    }
}
