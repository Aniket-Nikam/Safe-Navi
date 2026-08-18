package com.example.xavierproject;

public class ChatMessage {
    private String messageId;
    private String userId;
    private String userName;
    private String message;
    private long timestamp;
    private String userProfilePic; // Optional: for future enhancement

    public ChatMessage() {
        // Required empty constructor for Firebase
    }

    public ChatMessage(String messageId, String userId, String userName, String message, long timestamp) {
        this.messageId = messageId;
        this.userId = userId;
        this.userName = userName;
        this.message = message;
        this.timestamp = timestamp;
    }

    // Getters
    public String getMessageId() {
        return messageId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUserProfilePic() {
        return userProfilePic;
    }

    // Setters
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setUserProfilePic(String userProfilePic) {
        this.userProfilePic = userProfilePic;
    }
}
