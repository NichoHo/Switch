package com.switchpay.dashboard;

import com.switchpay.payment.store.PaymentEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Thymeleaf 3.1 no longer exposes #request by default; layout.html's nav-highlighting
    // needs the path as a plain context variable instead.
    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @GetMapping
    public String index() {
        return "redirect:/dashboard/payments";
    }

    @GetMapping("/payments")
    public String payments(Model model) {
        model.addAttribute("payments", dashboardService.getLatestPayments());
        return "payments";
    }

    @GetMapping("/payments/{id}")
    public String paymentDetail(@PathVariable UUID id, Model model) {
        PaymentEntity payment = dashboardService.findPayment(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payment"));
        model.addAttribute("payment", payment);
        model.addAttribute("events", dashboardService.getEvents(id));
        return "payment-detail";
    }

    @GetMapping("/risk")
    public String risk(Model model) {
        model.addAttribute("assessments", dashboardService.getLatestRiskAssessments());
        return "risk";
    }

    @GetMapping("/ledger")
    public String ledger(Model model) {
        Map<String, Long> balances = dashboardService.getTrialBalances();
        model.addAttribute("trialBalances", balances);
        model.addAttribute("outOfBalance", balances.values().stream().anyMatch(v -> v != 0L));
        return "ledger";
    }

    @GetMapping("/acquirers")
    public String acquirers(Model model) {
        model.addAttribute("acquirers", dashboardService.getAcquirers());
        return "acquirers";
    }

    @GetMapping("/settlement")
    public String settlement(Model model) {
        model.addAttribute("batches", dashboardService.getLatestSettlements());
        model.addAttribute("openExceptions", dashboardService.countOpenReconExceptions());
        return "settlement";
    }
}
