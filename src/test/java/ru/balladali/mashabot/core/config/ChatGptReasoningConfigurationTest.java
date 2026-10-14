package ru.balladali.mashabot.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptReasoningConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredClientSendsDisabledDefaultOrEnabledOverride(boolean enabled) throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(ClientsConfiguration.class)
                    .withBean(OkHttpClient.class, OkHttpClient::new)
                    .withPropertyValues("clients.chatgpt.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                            "clients.chatgpt.api-key=test-only", "clients.chatgpt.model=test-model",
                            "clients.chatgpt.timeout-sec=5", "clients.video-analyzer.base-url=http://127.0.0.1:1/analyze");
            // Omission must disable reasoning; explicitly setting true must reach the HTTP request.
            if (enabled) runner = runner.withPropertyValues("clients.chatgpt.reasoning-enabled=true");
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(enabled, context.getBean(ChatGptProperties.class).reasoningEnabled());
                String answer = context.getBean(ChatGptClient.class).chat(
                        List.of(new ChatGptClient.ChatMessage("user", "hello")), 0.8, 600);
                assertEquals("hello", answer);
                var json = new ObjectMapper().readTree(payload.get());
                assertTrue(json.path("reasoning").path("enabled").isBoolean());
                assertEquals(enabled, json.path("reasoning").path("enabled").booleanValue());
            });
        } finally { server.stop(0); }
    }
}
