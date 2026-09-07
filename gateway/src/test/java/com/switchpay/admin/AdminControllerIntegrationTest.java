package com.switchpay.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchpay.risk.RiskRuleMode;
import com.switchpay.risk.store.RulesetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Test
    public void rulesetCreationRequiresAdminKey() throws Exception {
        AdminController.CreateRulesetRequest request = new AdminController.CreateRulesetRequest(
                "v2", Map.of("VELOCITY_CARD_1H", RiskRuleMode.ACTIVE), true
        );

        mockMvc.perform(post("/admin/rulesets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void createAndActivateRuleset() throws Exception {
        AdminController.CreateRulesetRequest request = new AdminController.CreateRulesetRequest(
                "v3", Map.of("VELOCITY_CARD_1H", RiskRuleMode.SHADOW), true
        );

        mockMvc.perform(post("/admin/rulesets")
                .header("Authorization", "Bearer admin-secret-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertTrue(rulesetRepository.findById("v3").isPresent());
        assertTrue(rulesetRepository.findById("v3").get().isActive());
        assertEquals(1, rulesetRepository.findAll().stream().filter(r -> r.isActive()).count());
    }
}
