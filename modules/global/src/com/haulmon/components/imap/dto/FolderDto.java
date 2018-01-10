package com.haulmon.components.imap.dto;

import java.io.Serializable;
import java.util.List;

public class FolderDto implements Serializable {

    private String name;

    private String fullName;

    private boolean canHoldMessages;

    private List<FolderDto> children;

    public FolderDto(String name, String fullName, boolean canHoldMessages, List<FolderDto> children) {
        this.name = name;
        this.fullName = fullName;
        this.canHoldMessages = canHoldMessages;
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isCanHoldMessages() {
        return canHoldMessages;
    }

    public void setCanHoldMessages(boolean canHoldMessages) {
        this.canHoldMessages = canHoldMessages;
    }

    public List<FolderDto> getChildren() {
        return children;
    }

    public void setChildren(List<FolderDto> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return "FolderDto{" +
                "name='" + name + '\'' +
                ", fullName='" + fullName + '\'' +
                ", canHoldMessages=" + canHoldMessages +
                ", children=" + children +
                '}';
    }
}
