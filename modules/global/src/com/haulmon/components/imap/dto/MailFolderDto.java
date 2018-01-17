package com.haulmon.components.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.AbstractNotPersistentEntity;

import java.io.Serializable;
import java.util.List;

@NamePattern("%s |fullName")
@MetaClass(name = "mailcomponent$MailFolderDto")
public class MailFolderDto extends AbstractNotPersistentEntity {

    @MetaProperty(mandatory = true)
    private String name;

    @MetaProperty(mandatory = true)
    private String fullName;

    @MetaProperty(mandatory = true)
    private Boolean canHoldMessages;

    @MetaProperty
    private List<MailFolderDto> children;

    @MetaProperty
    private MailFolderDto parent;

    @MetaProperty
    private Boolean selected;

    public MailFolderDto(String name, String fullName, boolean canHoldMessages, List<MailFolderDto> children) {
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

    public Boolean getCanHoldMessages() {
        return canHoldMessages;
    }

    public void setCanHoldMessages(Boolean canHoldMessages) {
        this.canHoldMessages = canHoldMessages;
    }

    public List<MailFolderDto> getChildren() {
        return children;
    }

    public void setChildren(List<MailFolderDto> children) {
        this.children = children;
    }

    public MailFolderDto getParent() {
        return parent;
    }

    public void setParent(MailFolderDto parent) {
        this.parent = parent;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "MailFolderDto{" +
                "name='" + name + '\'' +
                ", fullName='" + fullName + '\'' +
                ", canHoldMessages=" + canHoldMessages +
                ", children=" + children +
                '}';
    }
}
