package com.example.xavierproject;

import java.util.HashMap;
import java.util.Map;

public class Comment {
    private String commentId;
    private String postId;
    private String userId;
    private String userName;
    private String content;
    private long timestamp;
    private int likes;
    private Map<String, Boolean> likedBy;
    private boolean isEdited;
    private long editedTimestamp;

    public Comment() {
        this.likedBy = new HashMap<>();
        this.isEdited = false;
    }

    public Comment(String commentId, String postId, String userId, String userName, String content, long timestamp) {
        this.commentId = commentId;
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.timestamp = timestamp;
        this.likes = 0;
        this.likedBy = new HashMap<>();
        this.isEdited = false;
        this.editedTimestamp = 0;
    }

    // Getters
    public String getCommentId() {
        return commentId;
    }

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

    public int getLikes() {
        return likes;
    }

    public Map<String, Boolean> getLikedBy() {
        return likedBy != null ? likedBy : new HashMap<>();
    }

    public boolean isEdited() {
        return isEdited;
    }

    public long getEditedTimestamp() {
        return editedTimestamp;
    }

    // Setters
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

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

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public void setLikedBy(Map<String, Boolean> likedBy) {
        this.likedBy = likedBy;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public void setEditedTimestamp(long editedTimestamp) {
        this.editedTimestamp = editedTimestamp;
    }
}
