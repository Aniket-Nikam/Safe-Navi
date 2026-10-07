package com.safenavi.app.safety.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatasetRouteEvaluation {
    private final List<RouteSafetyResult> rankedResults;
    private final String timePeriod;
    private final double coverageRatio;
    private final String factorSummary;
    private final String provider;
    private final String modelVersion;
    private final double confidence;

    public DatasetRouteEvaluation(List<RouteSafetyResult> rankedResults, String timePeriod,
                                  double coverageRatio, String factorSummary, String provider,
                                  String modelVersion, double confidence) {
        this.rankedResults = new ArrayList<>(rankedResults);
        this.timePeriod = timePeriod;
        this.coverageRatio = coverageRatio;
        this.factorSummary = factorSummary;
        this.provider = provider;
        this.modelVersion = modelVersion;
        this.confidence = confidence;
    }

    public List<RouteSafetyResult> getRankedResults() { return Collections.unmodifiableList(rankedResults); }
    public String getTimePeriod() { return timePeriod; }
    public double getCoverageRatio() { return coverageRatio; }
    public String getFactorSummary() { return factorSummary; }
    public String getProvider() { return provider; }
    public String getModelVersion() { return modelVersion; }
    public double getConfidence() { return confidence; }
}
