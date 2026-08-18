package com.example.xavierproject.safety.demo;

import com.example.xavierproject.safety.data.InMemorySafetyRepository;
import com.example.xavierproject.safety.risk.RuleBasedSafetyRiskEngine;
import com.example.xavierproject.safety.risk.SafetyRiskEngine;
import com.example.xavierproject.safety.service.HazardWorkflowService;

public final class SafetyDemoStore {
    private static InMemorySafetyRepository repository;
    private static SafetyRiskEngine riskEngine;
    private static HazardWorkflowService workflow;

    private SafetyDemoStore() {
    }

    public static synchronized InMemorySafetyRepository repository() {
        if (repository == null) repository = SyntheticDemoData.create(System.currentTimeMillis());
        return repository;
    }

    public static synchronized SafetyRiskEngine riskEngine() {
        if (riskEngine == null) riskEngine = new RuleBasedSafetyRiskEngine();
        return riskEngine;
    }

    public static synchronized HazardWorkflowService workflow() {
        if (workflow == null) workflow = new HazardWorkflowService(repository());
        return workflow;
    }

    public static synchronized void reset() {
        repository = SyntheticDemoData.create(System.currentTimeMillis());
        workflow = new HazardWorkflowService(repository);
        riskEngine = new RuleBasedSafetyRiskEngine();
    }
}
