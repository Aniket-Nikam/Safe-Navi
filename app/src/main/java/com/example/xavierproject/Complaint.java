package com.example.xavierproject;

public class Complaint {
    private String id;
    private String title;
    private String description;
    private String status;
    private String date;
    private String location;
    private String category;
    private String imageUrl;
    private String userId;
    private String userName;
    private String userEmail;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String reportId; // Store the actual Firebase report ID

    // Empty constructor needed for Firebase
    public Complaint() {
    }

    public Complaint(String id, String title, String description, String status, String date, String location) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.date = date;
        this.location = location;
    }

    public Complaint(String category, String description, String imageUrl, double latitude, double longitude) {
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReportId() {
        return reportId;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }
}
