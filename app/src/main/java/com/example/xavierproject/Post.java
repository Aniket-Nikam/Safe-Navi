package com.example.xavierproject;

import java.util.HashMap;
import java.util.Map;

public class Post {
    private String postId;
    private String userId;
    private String userName;
    private String content;
    private long timestamp;
    private int upvotes;
    private int commentsCount;
    private Map<String, Boolean> upvotedBy;
    private Map<String, Boolean> bookmarkedBy;
    private boolean isPinned;
    private String category;
    private long lastActivityTimestamp;

    public Post() {
        this.upvotedBy = new HashMap<>();
        this.bookmarkedBy = new HashMap<>();
        this.isPinned = false;
        this.category = "general";
        this.lastActivityTimestamp = System.currentTimeMillis();
    }

    public Post(String postId, String userId, String userName, String content, long timestamp) {
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.timestamp = timestamp;
        this.upvotes = 0;
        this.commentsCount = 0;
        this.upvotedBy = new HashMap<>();
        this.bookmarkedBy = new HashMap<>();
        this.isPinned = false;
        this.category = "general";
        this.lastActivityTimestamp = timestamp;
    }

    // Getters
    public String getPostId() {
        return postId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public Map<String, Boolean> getUpvotedBy() {
        return upvotedBy != null ? upvotedBy : new HashMap<>();
    }

    public Map<String, Boolean> getBookmarkedBy() {
        return bookmarkedBy != null ? bookmarkedBy : new HashMap<>();
    }

    public boolean isPinned() {
        return isPinned;
    }

    public String getCategory() {
        return category != null ? category : "general";
    }

    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    // Setters
    public void setPostId(String postId) {
        this.postId = postId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public void setCommentsCount(int commentsCount) {
        this.commentsCount = commentsCount;
    }

    public void setUpvotedBy(Map<String, Boolean> upvotedBy) {
        this.upvotedBy = upvotedBy;
    }

    public void setBookmarkedBy(Map<String, Boolean> bookmarkedBy) {
        this.bookmarkedBy = bookmarkedBy;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setLastActivityTimestamp(long lastActivityTimestamp) {
        this.lastActivityTimestamp = lastActivityTimestamp;
    }
}
