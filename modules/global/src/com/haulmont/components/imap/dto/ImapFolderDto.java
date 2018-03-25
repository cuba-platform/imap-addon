package com.haulmont.components.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.AbstractNotPersistentEntity;
import com.sun.mail.imap.IMAPFolder;

import java.util.List;

@NamePattern("%s |fullName")
@MetaClass(name = "imapcomponent$ImapFolderDto")
public class ImapFolderDto extends AbstractNotPersistentEntity {

    @MetaProperty(mandatory = true)
    private String name;

    @MetaProperty(mandatory = true)
    private String fullName;

    @MetaProperty(mandatory = true)
    private Boolean canHoldMessages = false;

    @MetaProperty
    private List<ImapFolderDto> children;

    @MetaProperty
    private ImapFolderDto parent;

    @MetaProperty
    private Boolean selected;

    private transient IMAPFolder imapFolder;

    public List<ImapFolderDto> getChildren() {
        return children;
    }

    public void setChildren(List<ImapFolderDto> children) {
        this.children = children;
    }


    public ImapFolderDto getParent() {
        return parent;
    }

    public void setParent(ImapFolderDto parent) {
        this.parent = parent;
    }


    public ImapFolderDto(String name, String fullName, boolean canHoldMessages, List<ImapFolderDto> children) {
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

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public IMAPFolder getImapFolder() {
        return imapFolder;
    }

    public void setImapFolder(IMAPFolder imapFolder) {
        this.imapFolder = imapFolder;
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
