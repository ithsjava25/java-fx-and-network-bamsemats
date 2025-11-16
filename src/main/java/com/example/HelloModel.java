package com.example;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class HelloModel {
    private final String TOPIC_URL;
    private final Set<String> seenIds = Collections.synchronizedSet(new HashSet<>());

    public HelloModel() {
        TOPIC_URL = EnvLoader.get("NTFY_URL");
        if (TOPIC_URL == null || TOPIC_URL.isBlank()) {
            throw new IllegalStateException("NTFY_URL not found in .env file");
        }
    }

    public HelloModel(String topicUrl) {
        if (topicUrl == null || topicUrl.isBlank()) {
            throw new IllegalStateException("NTFY_URL not found in .env file");
        }
        this.TOPIC_URL = topicUrl;
    }

    public void sendMessage(String username, String message) throws IOException {
        JSONObject json = new JSONObject();
        json.put("username", username == null || username.isBlank() ? "Anonymous" : username);
        json.put("message", message == null ? "" : message);
        json.put("time", Instant.now().getEpochSecond());
        sendJsonToNtfy(json);
    }

    protected void sendFile(String username, File file) {
        if (file == null || !file.exists()) return;
        final String safeUsername = (username == null || username.isBlank()) ? "Anonymous" : username;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOPIC_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");

                String mimeType = Files.probeContentType(file.toPath());
                if (mimeType == null) mimeType = "application/octet-stream";

                conn.setRequestProperty("Filename", file.getName());
                conn.setRequestProperty("Content-Type", mimeType);
                conn.setRequestProperty("X-Hide", "true");
                conn.setFixedLengthStreamingMode(file.length());
                conn.connect();

                try (OutputStream os = conn.getOutputStream()) {
                    Files.copy(file.toPath(), os);
                    os.flush();
                }

                int rc = conn.getResponseCode();
                InputStream respStream = rc >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String responseJson = "";
                if (respStream != null) responseJson = new String(respStream.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("PUT upload response code: " + rc);
                if (!responseJson.isBlank()) System.out.println("PUT upload response body: " + responseJson);

                String fileUrl = null;
                try {
                    if (!responseJson.isBlank()) {
                        JSONObject resp = new JSONObject(responseJson);
                        if (resp.has("attachment")) {
                            JSONObject attach = resp.getJSONObject("attachment");
                            if (attach.has("url")) fileUrl = attach.getString("url");
                        } else if (resp.has("url")) {
                            fileUrl = resp.getString("url");
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to parse upload response JSON: " + ex.getMessage());
                }

                if (fileUrl == null && rc >= 200 && rc < 300) {
                    if (!TOPIC_URL.endsWith("/")) fileUrl = TOPIC_URL + "/" + file.getName();
                    else fileUrl = TOPIC_URL + file.getName();
                }

                JSONObject msg = new JSONObject();
                msg.put("username", safeUsername);
                msg.put("message", "Sent file: " + file.getName());
                msg.put("time", Instant.now().getEpochSecond());
                msg.put("fileName", file.getName());
                msg.put("fileUrl", fileUrl);
                msg.put("mimeType", mimeType);

                sendJsonToNtfy(msg);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    protected void sendJsonToNtfy(JSONObject json) throws IOException {
        byte[] out = json.toString().getBytes(StandardCharsets.UTF_8);

        URL url = new URL(TOPIC_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setFixedLengthStreamingMode(out.length);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
            os.flush();
        }

        int rc = conn.getResponseCode();
        System.out.println("sendJsonToNtfy() -> response: " + rc);
        if (rc < 200 || rc >= 300) {
            InputStream err = conn.getErrorStream();
            if (err != null) {
                String body = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                System.err.println("ntfy error body: " + body);
            }
        }

        try (InputStream is = conn.getInputStream()) { if (is != null) is.readAllBytes(); } catch (IOException ignored) {}
        conn.disconnect();
    }

    public void loadHistory(Consumer<ChatMessage> callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOPIC_URL + "/json?since=all");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;

                        try {
                            JSONObject envelope = new JSONObject(line);
                            String id = envelope.optString("id", null);
                            if (id != null && !seenIds.add(id)) continue;

                            String rawMsg = envelope.optString("message", "").trim();
                            if (!rawMsg.startsWith("{") || !rawMsg.endsWith("}")) continue;

                            ChatMessage msg = parseEnvelopeToChatMessage(envelope);
                            if (msg != null) callback.accept(msg);

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }


    public void listenForMessages(Consumer<ChatMessage> callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOPIC_URL + "/json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;

                        try {
                            JSONObject envelope = new JSONObject(line);
                            String id = envelope.optString("id", null);
                            if (id != null && !seenIds.add(id)) continue;

                            String rawMsg = envelope.optString("message", "").trim();
                            if (!rawMsg.startsWith("{") || !rawMsg.endsWith("}")) continue;

                            ChatMessage msg = parseEnvelopeToChatMessage(envelope);
                            if (msg != null) callback.accept(msg);

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ntfy-listener-thread").start();
    }



    protected ChatMessage parseEnvelopeToChatMessage(JSONObject envelope) {
        String rawMsg = envelope.optString("message", null);
        if (rawMsg == null) return null;
        if (!rawMsg.startsWith("{") || !rawMsg.endsWith("}")) return null;
        try {
            String id = envelope.optString("id", null);
            long envelopeTime = envelope.optLong("time", Instant.now().getEpochSecond());
            String timestamp = Instant.ofEpochSecond(envelopeTime)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            String username = envelope.optString("username", "unknown");

            if (envelope.has("message")) {
                rawMsg = envelope.getString("message");
                JSONObject inner = null;
                try { inner = new JSONObject(rawMsg); } catch (Exception ignored) {}

                if (inner != null && inner.length() > 0) {
                    username = inner.optString("username", username);
                    String messageText = inner.optString("message", "");
                    String fileName = inner.optString("fileName", null);
                    String fileUrl = inner.optString("fileUrl", null);
                    String mimeType = inner.optString("mimeType", null);

                    if (fileName != null || fileUrl != null)
                        return new ChatMessage(id, username, messageText, timestamp, fileName, fileUrl, mimeType);
                    else
                        return new ChatMessage(id, username, messageText, timestamp);
                } else {
                    return new ChatMessage(id, username, rawMsg, timestamp);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

}
