package com.safenavi.app.safety.model;

public class DatasetPointRisk {
    private final double riskScore;
    private final double safetyScore;
    private final String riskLabel;
    private final String areaName;
    private final String timePeriod;

    public DatasetPointRisk(double riskScore, double safetyScore, String riskLabel,
                            String areaName, String timePeriod) {
        this.riskScore = riskScore;
        this.safetyScore = safetyScore;
        this.riskLabel = riskLabel;
        this.areaName = areaName;
        this.timePeriod = timePeriod;
    }

    public double getRiskScore() { return riskScore; }
    public double getSafetyScore() { return safetyScore; }
    public String getRiskLabel() { return riskLabel; }
    public String getAreaName() { return areaName; }
    public String getTimePeriod() { return timePeriod; }
}
