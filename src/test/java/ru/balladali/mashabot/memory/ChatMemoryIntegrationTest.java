package ru.balladali.mashabot.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Instant;
import java.util.concurrent.*;

class ChatMemoryIntegrationTest {
    static PostgreSQLContainer<?> db;
    static String url, user, password;

    @BeforeAll
    static void database() {
        url = System.getenv("MEMORY_TEST_URL");
        if (url != null) {
            user = "postgres";
            password = "";
        } else {
            Assumptions.assumeTrue(
                    org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                    "Docker unavailable");
            db = new PostgreSQLContainer<>("postgres:17-alpine");
            db.start();
            url = db.getJdbcUrl();
            user = db.getUsername();
            password = db.getPassword();
        }
    }

    @AfterAll
    static void stop() {
        if (db != null) db.stop();
    }

    MemoryProperties p;
    ChatMemoryService memory;

    @BeforeEach
    void setup() throws Exception {
        p = new MemoryProperties();
        p.getCapture().setDefaultChatEnabled(true); // These scenarios explicitly enable capture.
        p.setUrl(url);
        p.setUsername(user);
        p.setPassword(password);
        memory = new ChatMemoryService(p);
        try (var c = java.sql.DriverManager.getConnection(url, user, password);
                var s = c.createStatement()) {
            s.execute("TRUNCATE chat_messages,chat_memory_settings");
        }
    }

    ChatMessage msg(long chat, long id, Long thread, String text, Instant edit) {
        return new ChatMessage(
                chat,
                id,
                thread,
                null,
                null,
                "User",
                false,
                "INCOMING",
                null,
                "TEXT",
                text,
                Instant.ofEpochSecond(100),
                edit);
    }

    @Test
    void isolationEditsAndRestart() throws Exception {
        memory.save(msg(1, 100, null, "Secret A", null));
        memory.save(msg(2, 100, null, "Secret B", null));
        memory.save(msg(1, 100, null, "duplicate", null));
        assertEquals("Secret A", memory.find(1, 100).orElseThrow().text());
        memory.save(msg(1, 100, null, "edited", Instant.ofEpochSecond(200)));
        memory.save(msg(1, 100, null, "stale", Instant.ofEpochSecond(150)));
        memory.save(msg(1, 100, null, "equal", Instant.ofEpochSecond(200)));
        assertEquals("edited", new ChatMemoryService(p).find(1, 100).orElseThrow().text());
        assertEquals("Secret B", memory.find(2, 100).orElseThrow().text());
        memory.save(msg(1, 101, 7L, "topic", null));
        assertEquals(1, memory.recent(1, null, 30).size());
        assertEquals(1, memory.recent(1, 7L, 30).size());
    }

    @Test
    void settingsAndGlobalPrecedence() throws Exception {
        memory.save(msg(1, 1, null, "before", null));
        memory.setCapture(1, false);
        memory.save(msg(1, 2, null, "blocked", null));
        memory.save(msg(2, 2, null, "allowed", null));
        assertTrue(memory.find(1, 2).isEmpty());
        assertTrue(memory.find(1, 1).isPresent());
        assertTrue(memory.find(2, 2).isPresent());
        assertTrue(new ChatMemoryService(p).status(1).endsWith("false"));
        memory.setCapture(1, true);
        memory.save(msg(1, 2, null, "allowed", null));
        assertTrue(memory.find(1, 2).isPresent());
        p.getCapture().setEnabled(false);
        memory.save(msg(1, 3, null, "blocked", null));
        assertTrue(memory.find(1, 3).isEmpty());
        p.setEnabled(false);
        assertTrue(memory.recent(1, null, 30).isEmpty());
    }

    @Test
    void concurrentDuplicates() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 12; i++)
                tasks.add(
                        () -> {
                            memory.save(msg(1, 1, null, "same", null));
                            return null;
                        });
            for (var f : executor.invokeAll(tasks)) f.get();
        }
        assertEquals(1, memory.recent(1, null, 30).size());
    }

    @Test
    void pipelinePersistsSuccessfulResponseAndSurvivesRecreation() throws Exception {
        var first = telegram(1, "В субботу едем в Казань.", null);
        var second = telegram(2, "Во сколько?", first);
        var current = telegram(3, "Маша, ответь на вопрос.", second);
        memory.save(first, false);
        memory.save(second, false);
        memory.save(current, false);
        var llm =
                org.mockito.Mockito.mock(
                        ru.balladali.mashabot.core.clients.gpt.ChatGptClient.class);
        org.mockito.Mockito.when(
                        llm.chat(
                                org.mockito.ArgumentMatchers.anyList(),
                                org.mockito.ArgumentMatchers.anyDouble(),
                                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("Время пока не указано.");
        var client =
                org.mockito.Mockito.mock(
                        org.telegram.telegrambots.meta.generics.TelegramClient.class);
        var sent = telegram(4, "Время пока не указано.", current);
        org.mockito.Mockito.when(
                        client.execute(
                                org.mockito.ArgumentMatchers.any(
                                        org.telegram.telegrambots.meta.api.methods.send.SendMessage
                                                .class)))
                .thenReturn(sent);
        var delivery = new ru.balladali.mashabot.telegram.TelegramDelivery();
        org.springframework.test.util.ReflectionTestUtils.setField(delivery, "memory", memory);
        try (var executor =
                new ru.balladali.mashabot.core.services.KeyedTaskExecutor(
                        "test-summary", 1, 8, 1)) {
            var profiles =
                    org.mockito.Mockito.mock(
                            ru.balladali.mashabot.core.services.ProfileSummaryService.class);
            var handler =
                    new ru.balladali.mashabot.core.handlers.message.GptConversationHandler(
                            llm, "persona", 1500, 20, 30, 1, profiles, executor, delivery);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    handler, "persistentContext", new ChatContextBuilder(memory, p));
            handler.handle(new ru.balladali.mashabot.telegram.TelegramMessage(current, client));
            org.mockito.Mockito.verify(llm)
                    .chat(
                            org.mockito.ArgumentMatchers.argThat(
                                    messages ->
                                            messages.size() == 4
                                                    && messages.get(1).content().contains("Казань")
                                                    && messages.get(2)
                                                            .content()
                                                            .contains("Во сколько")),
                            org.mockito.ArgumentMatchers.eq(0.8),
                            org.mockito.ArgumentMatchers.eq(1500));
            org.mockito.Mockito.verifyNoInteractions(profiles);
        }
        assertEquals("OUTGOING", new ChatMemoryService(p).find(1, 4).orElseThrow().direction());
        memory.setCapture(1, false);
        memory.save(telegram(5, "Не сохранять", null), true);
        assertTrue(memory.find(1, 5).isEmpty());
    }

    private org.telegram.telegrambots.meta.api.objects.message.Message telegram(
            int id, String text, org.telegram.telegrambots.meta.api.objects.message.Message reply) {
        var m =
                org.mockito.Mockito.mock(
                        org.telegram.telegrambots.meta.api.objects.message.Message.class);
        org.mockito.Mockito.when(m.getChatId()).thenReturn(1L);
        org.mockito.Mockito.when(m.getMessageId()).thenReturn(id);
        org.mockito.Mockito.when(m.getDate()).thenReturn(100 + id);
        org.mockito.Mockito.when(m.hasText()).thenReturn(true);
        org.mockito.Mockito.when(m.getText()).thenReturn(text);
        org.mockito.Mockito.when(m.getReplyToMessage()).thenReturn(reply);
        return m;
    }
}
