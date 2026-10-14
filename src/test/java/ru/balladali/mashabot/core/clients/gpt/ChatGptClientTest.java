package ru.balladali.mashabot.core.clients.gpt;

import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptClientTest {
    @Test
    void emptyCompletionIsAnExplicitFailure() throws Exception {
        withResponse(200, "{\"choices\":[{\"message\":{\"content\":\" \"}}]}", 0, client ->
                assertThrows(IllegalStateException.class, () -> request(client)));
    }

    @Test
    void upstreamErrorBodyDoesNotLeakIntoException() throws Exception {
        withResponse(502, "sensitive-upstream-body", 0, client -> {
            Exception error = assertThrows(IllegalStateException.class, () -> request(client));
            assertFalse(error.getMessage().contains("sensitive"));
            assertTrue(error.getMessage().contains("502"));
        });
    }

    @Test
    void slowApiTimesOutInsteadOfWaitingForResponse() throws Exception {
        withResponse(200, "{}", 700, client -> {
            long started = System.nanoTime();
            assertThrows(java.io.IOException.class, () -> request(client));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 600);
        });
    }

    private void request(ChatGptClient client) throws Exception {
        client.chat(List.of(new ChatGptClient.ChatMessage("user", "hello")), 0.8, 10);
    }

    private void withResponse(int code, String body, long delay, Check check) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(code, data.length);
                exchange.getResponseBody().write(data);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            ChatGptClient client = new ChatGptClient(new OkHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-only", "test-model", Duration.ofMillis(100));
            check.run(client);
        } finally { server.stop(0); }
    }
    @FunctionalInterface interface Check { void run(ChatGptClient client) throws Exception; }
}
