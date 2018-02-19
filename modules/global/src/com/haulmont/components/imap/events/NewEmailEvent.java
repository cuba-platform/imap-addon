package com.haulmont.components.imap.events;

import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.entity.MailBox;

public class NewEmailEvent extends BaseImapEvent {

    public NewEmailEvent(MessageRef messageRef) {
        super(messageRef);
    }

    public NewEmailEvent(MailBox mailBox, String folderName, Long messageId) {
        this(new MessageRef(mailBox, folderName, messageId));
    }

    public MailBox getMailBox() {
        return messageRef.getMailBox();
    }

    public String getFolderName() {
        return messageRef.getFolderName();
    }

    public Long getMessageId() {
        return messageRef.getUid();
    }
}
