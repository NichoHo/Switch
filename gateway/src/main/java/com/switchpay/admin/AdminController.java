package com.switchpay.admin;

import com.switchpay.risk.RiskRuleMode;
import com.switchpay.risk.RulesetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RulesetService rulesetService;
    private final BacktestService backtestService;

    public AdminController(RulesetService rulesetService, BacktestService backtestService) {
        this.rulesetService = rulesetService;
        this.backtestService = backtestService;
    }

    @PostMapping("/rulesets")
    @ResponseStatus(HttpStatus.CREATED)
    public void createRuleset(@RequestBody CreateRulesetRequest request) {
        rulesetService.createRuleset(request.version(), request.rules(), request.activate());
    }

    @PostMapping("/rulesets/{version}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateRuleset(@PathVariable String version) {
        rulesetService.activateRuleset(version);
    }

    @PostMapping("/backtest")
    public BacktestService.BacktestReport runBacktest(@RequestBody BacktestRequest request) {
        return backtestService.runBacktest(request.rulesetVersion());
    }

    public record CreateRulesetRequest(String version, Map<String, RiskRuleMode> rules, boolean activate) {}
    public record BacktestRequest(String rulesetVersion) {}
}
