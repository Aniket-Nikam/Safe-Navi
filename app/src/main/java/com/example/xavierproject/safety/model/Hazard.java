package com.example.xavierproject.safety.model;

public class Hazard {
    private String id;
    private String title;
    private String categoryKey;
    private String description;
    private String reason;
    private HazardLocation location;
    private HazardSeverity severity = HazardSeverity.LOW;
    private HazardStatus status = HazardStatus.REPORTED;
    private boolean governmentVerified;
    private boolean synthetic;
    private String source = "CITIZEN";
    private String governmentEmployeeId;
    private int linkedReportCount;
    private int historicalRecurrence;
    private long createdAt;
    private long updatedAt;
    private Long resolvedAt;
    private Long validUntil;

    public Hazard() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public HazardLocation getLocation() { return location; }
    public void setLocation(HazardLocation location) { this.location = location; }
    public HazardSeverity getSeverity() { return severity; }
    public void setSeverity(HazardSeverity severity) { this.severity = severity; }
    public HazardStatus getStatus() { return status; }
    public void setStatus(HazardStatus status) { this.status = status; }
    public boolean isGovernmentVerified() { return governmentVerified; }
    public void setGovernmentVerified(boolean governmentVerified) { this.governmentVerified = governmentVerified; }
    public boolean isSynthetic() { return synthetic; }
    public void setSynthetic(boolean synthetic) { this.synthetic = synthetic; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getGovernmentEmployeeId() { return governmentEmployeeId; }
    public void setGovernmentEmployeeId(String governmentEmployeeId) { this.governmentEmployeeId = governmentEmployeeId; }
    public int getLinkedReportCount() { return linkedReportCount; }
    public void setLinkedReportCount(int linkedReportCount) { this.linkedReportCount = linkedReportCount; }
    public int getHistoricalRecurrence() { return historicalRecurrence; }
    public void setHistoricalRecurrence(int historicalRecurrence) { this.historicalRecurrence = historicalRecurrence; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public Long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getValidUntil() { return validUntil; }
    public void setValidUntil(Long validUntil) { this.validUntil = validUntil; }
}
