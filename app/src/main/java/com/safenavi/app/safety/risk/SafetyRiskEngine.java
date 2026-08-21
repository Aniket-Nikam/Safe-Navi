package com.safenavi.app.safety.risk;

import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.SafetyScore;
import java.util.List;

public interface SafetyRiskEngine {
    SafetyScore calculate(double latitude, double longitude, long atTimeMillis, List<Hazard> hazards);
}
