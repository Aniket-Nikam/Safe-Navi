package com.safenavi.app.safety.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatasetRouteEvaluation {
    private final List<RouteSafetyResult> rankedResults;
    private final String timePeriod;
    private final double coverageRatio;
    private final String factorSummary;

    public DatasetRouteEvaluation(List<RouteSafetyResult> rankedResults, String timePeriod,
                                  double coverageRatio, String factorSummary) {
        this.rankedResults = new ArrayList<>(rankedResults);
        this.timePeriod = timePeriod;
        this.coverageRatio = coverageRatio;
        this.factorSummary = factorSummary;
    }

    public List<RouteSafetyResult> getRankedResults() { return Collections.unmodifiableList(rankedResults); }
    public String getTimePeriod() { return timePeriod; }
    public double getCoverageRatio() { return coverageRatio; }
    public String getFactorSummary() { return factorSummary; }
}
