package com.domidodo.logx.sdk.core.buffer;

import com.domidodo.logx.sdk.core.model.LogEntry;

import java.util.ArrayList;
import java.util.List;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogBuffer {

    private final BlockingQueue<LogEntry> queue;
    private final int capacity;

    public LogBuffer(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public void add(LogEntry entry) {
        try {
            queue.offer(entry);
        } catch (Exception e) {
            // 缓冲区满，丢弃旧日志
            queue.poll();
            queue.offer(entry);
        }
    }

    public List<LogEntry> drain() {
        List<LogEntry> entries = new ArrayList<>();
        queue.drainTo(entries);
        return entries;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public boolean isFull() {
        return queue.size() >= capacity;
    }
}
