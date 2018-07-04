package com.haulmont.addon.imap.core;

public class Task<IN, OUT> {
    private final String description;
    private final ImapFunction<IN, OUT> action;
    private final boolean hasResult;

    public Task(String description, boolean hasResult, ImapFunction<IN, OUT> action) {
        this.description = description;
        this.action = action;
        this.hasResult = hasResult;
    }

    String getDescription() {
        return description;
    }

    ImapFunction<IN, OUT> getAction() {
        return action;
    }

    boolean isHasResult() {
        return hasResult;
    }

}
