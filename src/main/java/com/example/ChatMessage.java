package com.example;

public class ChatMessage {
    private final String id;
    private final String username;
    private final String message;
    private final String timestamp;
    private final String fileName;
    private final String fileUrl;
    private final String mimeType;

    public ChatMessage(String id, String username, String message, String timestamp,
                       String fileName, String fileUrl, String mimeType) {
        this.id = id;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.mimeType = mimeType;
    }

    public ChatMessage(String id, String username, String message, String timestamp) {
        this(id, username, message, timestamp, null, null, null);
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getFileName() { return fileName; }
    public String getFileUrl() { return fileUrl; }
    public String getMimeType() { return mimeType; }
}
