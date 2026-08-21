package com.safenavi.app.safety.model;

public class HazardHistory {
    private String id;
    private String hazardId;
    private HazardStatus previousStatus;
    private HazardStatus newStatus;
    private HazardSeverity previousSeverity;
    private HazardSeverity newSeverity;
    private String employeeId;
    private String action;
    private String reason;
    private long timestamp;

    public HazardHistory() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHazardId() { return hazardId; }
    public void setHazardId(String hazardId) { this.hazardId = hazardId; }
    public HazardStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(HazardStatus previousStatus) { this.previousStatus = previousStatus; }
    public HazardStatus getNewStatus() { return newStatus; }
    public void setNewStatus(HazardStatus newStatus) { this.newStatus = newStatus; }
    public HazardSeverity getPreviousSeverity() { return previousSeverity; }
    public void setPreviousSeverity(HazardSeverity previousSeverity) { this.previousSeverity = previousSeverity; }
    public HazardSeverity getNewSeverity() { return newSeverity; }
    public void setNewSeverity(HazardSeverity newSeverity) { this.newSeverity = newSeverity; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
