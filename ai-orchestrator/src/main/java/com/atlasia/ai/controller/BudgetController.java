package com.atlasia.ai.controller;

import com.atlasia.ai.service.BudgetTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    private final BudgetTracker budgetTracker;

    public BudgetController(BudgetTracker budgetTracker) {
        this.budgetTracker = budgetTracker;
    }

    @GetMapping
    public ResponseEntity<BudgetTracker.BudgetSnapshot> getBudget(
            @RequestParam(required = false) UUID runId) {
        return ResponseEntity.ok(budgetTracker.snapshot(runId));
    }
}
