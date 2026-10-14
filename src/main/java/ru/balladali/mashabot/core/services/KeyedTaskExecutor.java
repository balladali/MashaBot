package ru.balladali.mashabot.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/** Bounded work queue, FIFO within a key, parallel execution between keys. */
public final class KeyedTaskExecutor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(KeyedTaskExecutor.class);
    private final ExecutorService workers;
    private final Map<String, ArrayDeque<Runnable>> queues = new HashMap<>();
    private final int capacity;
    private final int perKeyCapacity;
    private int outstanding;
    private boolean closed;

    public KeyedTaskExecutor(String name, int threads, int capacity, int perKeyCapacity) {
        if (threads < 1 || capacity < 1 || perKeyCapacity < 1) throw new IllegalArgumentException("Limits must be positive");
        this.capacity = capacity;
        this.perKeyCapacity = perKeyCapacity;
        workers = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name(name + "-", 0).factory());
    }

    public synchronized boolean submit(String key, Runnable task) {
        ArrayDeque<Runnable> queue = queues.get(key);
        if (closed || outstanding >= capacity || (queue != null && queue.size() >= perKeyCapacity)) return false;
        boolean newKey = queue == null;
        if (newKey) { queue = new ArrayDeque<>(); queues.put(key, queue); }
        queue.addLast(task);
        outstanding++;
        if (newKey) workers.execute(() -> drain(key));
        return true;
    }

    private void drain(String key) {
        while (true) {
            Runnable task;
            synchronized (this) { task = queues.get(key).peekFirst(); }
            boolean finished;
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("event=task_failed type={}", e.getClass().getSimpleName());
            } finally {
                synchronized (this) {
                    outstanding--;
                    ArrayDeque<Runnable> queue = queues.get(key);
                    queue.removeFirst();
                    if (Thread.currentThread().isInterrupted()) {
                        outstanding -= queue.size();
                        queue.clear();
                    }
                    finished = queue.isEmpty();
                    if (finished) queues.remove(key);
                }
            }
            if (finished) return;
        }
    }

    public synchronized int outstanding() { return outstanding; }

    @Override
    public void close() {
        synchronized (this) { closed = true; }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) workers.shutdownNow();
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
