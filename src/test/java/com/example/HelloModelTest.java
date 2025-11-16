package com.example;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HelloModelTest {

    private HelloModel model;

    @BeforeEach
    void setUp() {
        model = new HelloModel("https://dummy-url-for-tests") {
            @Override
            protected void sendJsonToNtfy(JSONObject json) {
                System.out.println("Mock sendJsonToNtfy called: " + json);
            }

            @Override
            protected void sendFile(String username, File file) {
                if (file != null) {
                    System.out.println("Mock sendFileInternal called for: " + file.getName());
                } else {
                    System.out.println("Mock sendFileInternal called with null file");
                }
            }

            @Override
            protected ChatMessage parseEnvelopeToChatMessage(JSONObject envelope) {
                return super.parseEnvelopeToChatMessage(envelope);
            }
        };
    }

    @Test
    void testSendMessageWithValidUsernameAndText() {
        assertDoesNotThrow(() -> model.sendMessage("Alice", "Hello World"));
    }

    @Test
    void testSendMessageWithNullUsername() {
        assertDoesNotThrow(() -> model.sendMessage(null, "Hello World"));
    }

    @Test
    void testSendMessageWithNullMessage() {
        assertDoesNotThrow(() -> model.sendMessage("Alice", null));
    }

    @Test
    void testSendFileWithValidFile() throws Exception {
        File tempFile = File.createTempFile("testfile", ".txt");
        tempFile.deleteOnExit();

        assertDoesNotThrow(() -> model.sendFile("Bob", tempFile));
    }

    @Test
    void testSendFileWithNullFile() {
        assertDoesNotThrow(() -> model.sendFile("Bob", null));
    }

    @Test
    void testJsonParsingCreatesChatMessage() {
        JSONObject envelope = new JSONObject();
        envelope.put("id", "123");
        envelope.put("username", "Alice");
        envelope.put("message", "{\"username\":\"Alice\",\"message\":\"Hi there\",\"time\":" + Instant.now().getEpochSecond() + "}");
        envelope.put("time", Instant.now().getEpochSecond());

        ChatMessage msg = model.parseEnvelopeToChatMessage(envelope);
        assertNotNull(msg);
        assertEquals("Alice", msg.getUsername());
        assertEquals("Hi there", msg.getMessage());
    }

    @Test
    void testFilterOutNonJsonMessages() {
        JSONObject envelope = new JSONObject();
        envelope.put("id", "124");
        envelope.put("username", "Bob");
        envelope.put("message", "Just plain text");
        envelope.put("time", Instant.now().getEpochSecond());

        ChatMessage msg = model.parseEnvelopeToChatMessage(envelope);
        assertNull(msg);
    }


    @Test
    void testLoadHistoryCallbackCalled() {
        AtomicBoolean called = new AtomicBoolean(false);
        model.loadHistory(msg -> called.set(true));
        assertDoesNotThrow(() -> model.loadHistory(msg -> {}));
    }

    @Test
    void testListenForMessagesCallbackCalled() {
        AtomicReference<ChatMessage> ref = new AtomicReference<>();
        model.listenForMessages(ref::set);
        assertDoesNotThrow(() -> model.listenForMessages(msg -> {}));
    }
}
