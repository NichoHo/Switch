package com.switchpay.acqsim;

import com.switchpay.contracts.CaptureNotification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapturesController {

    private final CaptureStore captureStore;

    public CapturesController(CaptureStore captureStore) {
        this.captureStore = captureStore;
    }

    @PostMapping("/captures")
    public ResponseEntity<Void> capture(@RequestBody CaptureNotification notification) {
        captureStore.record(new CaptureStore.Capture(
            notification.reference(), notification.amountMinor(), notification.currency(), notification.businessDate()));
        return ResponseEntity.ok().build();
    }
}
