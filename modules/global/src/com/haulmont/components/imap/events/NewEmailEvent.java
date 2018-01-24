package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.MailBox;

public class NewEmailEvent extends BaseImapEvent {

    private final MailBox mailBox;
    private final String folderName;
    private final Long messageId;

    public NewEmailEvent(MailBox mailBox, String folderName, Long messageId) {
        super(mailBox);

        this.mailBox = mailBox;
        this.folderName = folderName;
        this.messageId = messageId;
    }

    @Override
    public MailBox getSource() {
        return mailBox;
    }

    public MailBox getMailBox() {
        return mailBox;
    }

    public String getFolderName() {
        return folderName;
    }

    public Long getMessageId() {
        return messageId;
    }
}
