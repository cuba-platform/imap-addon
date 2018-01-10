package com.haulmon.components.imap.events;

import com.haulmon.components.imap.entity.MailBox;
import org.springframework.context.ApplicationEvent;

public class NewEmailEvent extends ApplicationEvent {

    private final MailBox mailBox;
    private final String folderName;
    private final String messageId;

    public NewEmailEvent(MailBox mailBox, String folderName, String messageId) {
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

    public String getMessageId() {
        return messageId;
    }
}
