package com.domidodo.logx.sdk.core;

import java.util.Map;

public class LogXLogger {

    private final String className;
    private static LogXClient client;

    private LogXLogger(String className) {
        this.className = className;
    }

    public static void initClient(LogXClient logXClient) {
        client = logXClient;
    }

    public static LogXLogger getLogger(Class<?> clazz) {
        return new LogXLogger(clazz.getName());
    }

    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Map<String, Object> context) {
        if (client != null) {
            Map<String, Object> fullContext = addClassName(context);
            client.info(message, fullContext);
        }
    }

    public void error(String message) {
        error(message, null, null);
    }

    public void error(String message, Throwable throwable) {
        error(message, throwable, null);
    }

    public void error(String message, Throwable throwable, Map<String, Object> context) {
        if (client != null) {
            Map<String, Object> fullContext = addClassName(context);
            client.error(message, throwable, fullContext);
        }
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Map<String, Object> context) {
        if (client != null) {
            Map<String, Object> fullContext = addClassName(context);
            client.warn(message, fullContext);
        }
    }

    public void debug(String message) {
        debug(message, null);
    }

    public void debug(String message, Map<String, Object> context) {
        if (client != null) {
            Map<String, Object> fullContext = addClassName(context);
            client.debug(message, fullContext);
        }
    }

    private Map<String, Object> addClassName(Map<String, Object> context) {
        Map<String, Object> newContext = context != null
                ? new java.util.HashMap<>(context)
                : new java.util.HashMap<>();
        newContext.put("className", className);
        return newContext;
    }
}
