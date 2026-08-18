package com.example.xavierproject.safety.risk;

import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.SafetyScore;
import java.util.List;

public interface SafetyRiskEngine {
    SafetyScore calculate(double latitude, double longitude, long atTimeMillis, List<Hazard> hazards);
}
