package ru.balladali.mashabot.core.services;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class KeyedTaskExecutorTest {
    @Test
    void slowChatDoesNotBlockAnotherChatAndKeepsItsOwnOrder() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch otherChat = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        List<Integer> order = new CopyOnWriteArrayList<>();
        try (KeyedTaskExecutor executor = new KeyedTaskExecutor("test", 2, 8, 4)) {
            try {
                assertTrue(executor.submit("a", () -> {
                    entered.countDown(); await(release); order.add(1);
                }));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertTrue(executor.submit("a", () -> { order.add(2); finished.countDown(); }));
                assertTrue(executor.submit("b", otherChat::countDown));
                assertTrue(otherChat.await(3, TimeUnit.SECONDS));
                assertTrue(order.isEmpty());
                release.countDown();
                assertTrue(finished.await(3, TimeUnit.SECONDS));
                assertEquals(List.of(1, 2), order);
            } finally { release.countDown(); }
        }
    }

    @Test
    void rejectsOverloadWithoutRunningOnCallingThreadAndRecoversAfterFailure() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch next = new CountDownLatch(1);
        try (KeyedTaskExecutor executor = new KeyedTaskExecutor("test", 1, 2, 1)) {
            try {
                assertTrue(executor.submit("a", () -> { entered.countDown(); await(release); throw new IllegalStateException(); }));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertFalse(executor.submit("a", () -> fail("per-chat limit")));
                assertTrue(executor.submit("b", next::countDown));
                assertFalse(executor.submit("c", () -> fail("global limit")));
                release.countDown();
                assertTrue(next.await(3, TimeUnit.SECONDS));
            } finally { release.countDown(); }
        }
    }

    static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
