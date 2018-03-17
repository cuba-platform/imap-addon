package com.haulmont.components.imap.core;

public class Task<IN, OUT> {
    private String description;
    private MessageFunction<IN, OUT> action;
    private boolean hasResult;

    public Task(String description, boolean hasResult, MessageFunction<IN, OUT> action) {
        this.description = description;
        this.action = action;
        this.hasResult = hasResult;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MessageFunction<IN, OUT> getAction() {
        return action;
    }

    public void setAction(MessageFunction<IN, OUT> action) {
        this.action = action;
    }

    public boolean isHasResult() {
        return hasResult;
    }

    public void setHasResult(boolean hasResult) {
        this.hasResult = hasResult;
    }

}
