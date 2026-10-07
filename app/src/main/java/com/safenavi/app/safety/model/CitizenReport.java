package com.safenavi.app.safety.model;

public class CitizenReport {
    private String id;
    private String citizenId;
    private String title;
    private String categoryKey;
    private String description;
    private HazardLocation location;
    private HazardStatus status = HazardStatus.REPORTED;
    private String linkedHazardId;
    private int confirmationCount;
    private long createdAt;
    private long updatedAt;

    public CitizenReport() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public HazardLocation getLocation() { return location; }
    public void setLocation(HazardLocation location) { this.location = location; }
    public HazardStatus getStatus() { return status; }
    public void setStatus(HazardStatus status) { this.status = status; }
    public String getLinkedHazardId() { return linkedHazardId; }
    public void setLinkedHazardId(String linkedHazardId) { this.linkedHazardId = linkedHazardId; }
    public int getConfirmationCount() { return confirmationCount; }
    public void setConfirmationCount(int confirmationCount) { this.confirmationCount = confirmationCount; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
