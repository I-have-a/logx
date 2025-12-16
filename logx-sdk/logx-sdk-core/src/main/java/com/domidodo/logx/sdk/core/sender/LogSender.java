package com.domidodo.logx.sdk.core.sender;

import com.domidodo.logx.sdk.core.model.LogEntry;

import java.util.List;

public interface LogSender {

    void send(LogEntry entry);

    void sendBatch(List<LogEntry> entries);
}
